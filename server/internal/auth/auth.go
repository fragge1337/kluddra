package auth

import (
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/id"
	"github.com/fragge1337/kluddra/server/internal/model"
	"golang.org/x/crypto/argon2"
)

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrInvalidToken       = errors.New("invalid or expired token")
	ErrTokenReused        = errors.New("refresh token reuse detected")
	ErrSetupNotAllowed    = errors.New("initial setup already completed")
	ErrInvalidSetupCode   = errors.New("invalid setup code")
	ErrUsernameTaken      = errors.New("username already exists")
	ErrValidation         = errors.New("validation error")
)

type Claims struct {
	Sub       string `json:"sub"`
	Username  string `json:"usr"`
	SessionID string `json:"sid"`
	IsAdmin   bool   `json:"adm"`
	IssuedAt  int64  `json:"iat"`
	ExpiresAt int64  `json:"exp"`
}

type Service struct {
	store     *db.Store
	cfg       config.Config
	jwtSecret []byte

	setupMu   sync.RWMutex
	setupCode string
}

// NewService initializes authentication, loading or generating the persistent JWT signing key.
func NewService(ctx context.Context, store *db.Store, cfg config.Config) (*Service, error) {
	secret, err := loadOrCreateJWTSecret(cfg.DataDir, cfg.JWTSecret)
	if err != nil {
		return nil, err
	}

	svc := &Service{
		store:     store,
		cfg:       cfg,
		jwtSecret: secret,
	}

	required, err := svc.IsSetupRequired(ctx)
	if err != nil {
		return nil, err
	}
	if required {
		if cfg.AdminSetupToken != "" {
			svc.setupCode = cfg.AdminSetupToken
		} else {
			code, err := generateSetupCode()
			if err != nil {
				return nil, err
			}
			svc.setupCode = code
		}
	}

	return svc, nil
}

// SetupCode returns the active one-time setup code if initial setup has not yet completed.
func (s *Service) SetupCode() string {
	s.setupMu.RLock()
	defer s.setupMu.RUnlock()
	return s.setupCode
}

// IsSetupRequired returns true when no user accounts exist yet.
func (s *Service) IsSetupRequired(ctx context.Context) (bool, error) {
	var count int
	if err := s.store.DB.QueryRowContext(ctx, `SELECT COUNT(*) FROM users`).Scan(&count); err != nil {
		return false, err
	}
	return count == 0, nil
}

// CompleteSetup creates the first administrator account using the bootstrap setup code.
func (s *Service) CompleteSetup(ctx context.Context, setupCode, username, password, deviceName string) (model.AuthTokensResponse, error) {
	required, err := s.IsSetupRequired(ctx)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}
	if !required {
		return model.AuthTokensResponse{}, ErrSetupNotAllowed
	}

	s.setupMu.RLock()
	expected := s.setupCode
	s.setupMu.RUnlock()

	if expected == "" || subtle.ConstantTimeCompare([]byte(strings.TrimSpace(setupCode)), []byte(expected)) != 1 {
		return model.AuthTokensResponse{}, ErrInvalidSetupCode
	}

	user, err := s.CreateUser(ctx, username, password, true)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	// Permanently clear bootstrap setup code once first admin exists.
	s.setupMu.Lock()
	s.setupCode = ""
	s.setupMu.Unlock()

	return s.issueSessionTokens(ctx, user, deviceName, "")
}

// CreateUser creates a new user account with an Argon2id-hashed password.
func (s *Service) CreateUser(ctx context.Context, username, password string, isAdmin bool) (model.User, error) {
	username = strings.TrimSpace(username)
	if err := validateCredentials(username, password); err != nil {
		return model.User{}, err
	}

	hash, err := s.HashPassword(password)
	if err != nil {
		return model.User{}, err
	}

	now := time.Now().UnixMilli()
	user := model.User{
		ID:           id.NewUUIDv7(),
		Username:     username,
		PasswordHash: hash,
		IsAdmin:      isAdmin,
		CreatedAt:    now,
		UpdatedAt:    now,
	}

	adminInt := 0
	if isAdmin {
		adminInt = 1
	}

	err = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		var existing int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM users WHERE username = ?`, username).Scan(&existing); err != nil {
			return err
		}
		if existing > 0 {
			return ErrUsernameTaken
		}
		_, err := tx.ExecContext(ctx, `
			INSERT INTO users (id, username, password_hash, is_admin, sync_seq, created_at, updated_at)
			VALUES (?, ?, ?, ?, 0, ?, ?)
		`, user.ID, user.Username, user.PasswordHash, adminInt, user.CreatedAt, user.UpdatedAt)
		return err
	})
	if err != nil {
		return model.User{}, err
	}
	return user, nil
}

// Login verifies username and password and issues a new token pair.
func (s *Service) Login(ctx context.Context, username, password, deviceName string) (model.AuthTokensResponse, error) {
	username = strings.TrimSpace(username)
	var user model.User
	var isAdmin int
	err := s.store.DB.QueryRowContext(ctx, `
		SELECT id, username, password_hash, is_admin, sync_seq, created_at, updated_at
		FROM users WHERE username = ?
	`, username).Scan(&user.ID, &user.Username, &user.PasswordHash, &isAdmin, &user.SyncSeq, &user.CreatedAt, &user.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		// Burn comparable CPU time to prevent username enumeration via timing.
		_, _ = s.HashPassword("dummy-password-for-timing")
		return model.AuthTokensResponse{}, ErrInvalidCredentials
	}
	if err != nil {
		return model.AuthTokensResponse{}, err
	}
	user.IsAdmin = isAdmin == 1

	ok, err := VerifyPassword(password, user.PasswordHash)
	if err != nil || !ok {
		return model.AuthTokensResponse{}, ErrInvalidCredentials
	}

	return s.issueSessionTokens(ctx, user, deviceName, "")
}

// Refresh rotates a refresh token, revoking the whole family if reuse is detected.
func (s *Service) Refresh(ctx context.Context, rawRefreshToken, deviceName string) (model.AuthTokensResponse, error) {
	rawRefreshToken = strings.TrimSpace(rawRefreshToken)
	if rawRefreshToken == "" {
		return model.AuthTokensResponse{}, ErrInvalidToken
	}

	tokenHash := sha256Hex(rawRefreshToken)
	now := time.Now().UnixMilli()

	var (
		sessionID   string
		userID      string
		tokenFamily string
		storedDev   string
		expiresAt   int64
		revokedAt   sql.NullInt64
	)

	err := s.store.DB.QueryRowContext(ctx, `
		SELECT id, user_id, token_family, device_name, expires_at, revoked_at
		FROM sessions WHERE token_hash = ?
	`, tokenHash).Scan(&sessionID, &userID, &tokenFamily, &storedDev, &expiresAt, &revokedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return model.AuthTokensResponse{}, ErrInvalidToken
	}
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	// Reuse detection: a previously rotated or revoked token was replayed!
	if revokedAt.Valid {
		_ = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
			_, e := tx.ExecContext(ctx, `
				UPDATE sessions SET revoked_at = COALESCE(revoked_at, ?)
				WHERE token_family = ?
			`, now, tokenFamily)
			return e
		})
		return model.AuthTokensResponse{}, ErrTokenReused
	}

	if now >= expiresAt {
		return model.AuthTokensResponse{}, ErrInvalidToken
	}

	var user model.User
	var isAdmin int
	if err := s.store.DB.QueryRowContext(ctx, `
		SELECT id, username, is_admin, sync_seq, created_at, updated_at
		FROM users WHERE id = ?
	`, userID).Scan(&user.ID, &user.Username, &isAdmin, &user.SyncSeq, &user.CreatedAt, &user.UpdatedAt); err != nil {
		return model.AuthTokensResponse{}, ErrInvalidToken
	}
	user.IsAdmin = isAdmin == 1

	// Revoke the used session row before issuing rotated replacement in same family.
	if err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		_, e := tx.ExecContext(ctx, `UPDATE sessions SET revoked_at = ?, last_used_at = ? WHERE id = ?`, now, now, sessionID)
		return e
	}); err != nil {
		return model.AuthTokensResponse{}, err
	}

	if strings.TrimSpace(deviceName) == "" {
		deviceName = storedDev
	}
	return s.issueSessionTokens(ctx, user, deviceName, tokenFamily)
}

// Logout revokes a specific session belonging to userID.
func (s *Service) Logout(ctx context.Context, userID, sessionID string) error {
	now := time.Now().UnixMilli()
	return s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		_, err := tx.ExecContext(ctx, `
			UPDATE sessions SET revoked_at = COALESCE(revoked_at, ?)
			WHERE id = ? AND user_id = ?
		`, now, sessionID, userID)
		return err
	})
}

// ListSessions returns active non-revoked sessions for the user.
func (s *Service) ListSessions(ctx context.Context, userID, currentSessionID string) ([]model.SessionInfo, error) {
	now := time.Now().UnixMilli()
	rows, err := s.store.DB.QueryContext(ctx, `
		SELECT id, device_name, created_at, last_used_at, expires_at
		FROM sessions
		WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
		ORDER BY last_used_at DESC
	`, userID, now)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	sessions := make([]model.SessionInfo, 0)
	for rows.Next() {
		var item model.SessionInfo
		if err := rows.Scan(&item.ID, &item.DeviceName, &item.CreatedAt, &item.LastUsedAt, &item.ExpiresAt); err != nil {
			return nil, err
		}
		item.Current = item.ID == currentSessionID
		sessions = append(sessions, item)
	}
	return sessions, rows.Err()
}

// VerifyAccessToken validates signature, expiry, and ensures the session has not been revoked.
func (s *Service) VerifyAccessToken(ctx context.Context, token string) (Claims, error) {
	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Claims{}, ErrInvalidToken
	}

	signingInput := parts[0] + "." + parts[1]
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return Claims{}, ErrInvalidToken
	}

	expectedSig := computeHS256(signingInput, s.jwtSecret)
	if !hmac.Equal(sig, expectedSig) {
		return Claims{}, ErrInvalidToken
	}

	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return Claims{}, ErrInvalidToken
	}

	var claims Claims
	if err := json.Unmarshal(payloadBytes, &claims); err != nil {
		return Claims{}, ErrInvalidToken
	}

	if time.Now().Unix() >= claims.ExpiresAt || claims.Sub == "" || claims.SessionID == "" {
		return Claims{}, ErrInvalidToken
	}

	// Ensure session is still active (supports immediate remote session revocation).
	var revokedAt sql.NullInt64
	var expiresAt int64
	err = s.store.DB.QueryRowContext(ctx, `
		SELECT revoked_at, expires_at FROM sessions WHERE id = ? AND user_id = ?
	`, claims.SessionID, claims.Sub).Scan(&revokedAt, &expiresAt)
	if err != nil || revokedAt.Valid || time.Now().UnixMilli() >= expiresAt {
		return Claims{}, ErrInvalidToken
	}

	return claims, nil
}

// HashPassword produces an Argon2id PHC-encoded password hash.
func (s *Service) HashPassword(password string) (string, error) {
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	hash := argon2.IDKey([]byte(password), salt, s.cfg.Argon2Time, s.cfg.Argon2MemoryKB, s.cfg.Argon2Parallelism, 32)
	return fmt.Sprintf("$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2.Version,
		s.cfg.Argon2MemoryKB,
		s.cfg.Argon2Time,
		s.cfg.Argon2Parallelism,
		base64.RawStdEncoding.EncodeToString(salt),
		base64.RawStdEncoding.EncodeToString(hash),
	), nil
}

// VerifyPassword validates a plaintext password against a PHC Argon2id hash in constant time.
func VerifyPassword(password, encoded string) (bool, error) {
	parts := strings.Split(encoded, "$")
	if len(parts) != 6 || parts[1] != "argon2id" {
		return false, errors.New("unsupported password hash format")
	}
	var memory, iterations uint32
	var parallelism uint8
	for _, kv := range strings.Split(parts[3], ",") {
		pair := strings.SplitN(kv, "=", 2)
		if len(pair) != 2 {
			return false, errors.New("malformed argon2 params")
		}
		val, err := strconv.ParseUint(pair[1], 10, 32)
		if err != nil {
			return false, err
		}
		switch pair[0] {
		case "m":
			memory = uint32(val)
		case "t":
			iterations = uint32(val)
		case "p":
			parallelism = uint8(val)
		}
	}
	salt, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil {
		return false, err
	}
	expectedHash, err := base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil {
		return false, err
	}

	computed := argon2.IDKey([]byte(password), salt, iterations, memory, parallelism, uint32(len(expectedHash)))
	return subtle.ConstantTimeCompare(computed, expectedHash) == 1, nil
}

func (s *Service) issueSessionTokens(ctx context.Context, user model.User, deviceName, existingFamily string) (model.AuthTokensResponse, error) {
	deviceName = sanitizeDeviceName(deviceName)
	rawRefresh, err := randomBase64URL(32)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	now := time.Now()
	nowMs := now.UnixMilli()
	expiresMs := now.Add(s.cfg.RefreshTokenTTL).UnixMilli()
	sessionID := id.NewUUIDv7()
	family := existingFamily
	if family == "" {
		family = id.NewUUIDv7()
	}

	err = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		_, e := tx.ExecContext(ctx, `
			INSERT INTO sessions (id, user_id, token_hash, token_family, device_name, created_at, last_used_at, expires_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		`, sessionID, user.ID, sha256Hex(rawRefresh), family, deviceName, nowMs, nowMs, expiresMs)
		return e
	})
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	claims := Claims{
		Sub:       user.ID,
		Username:  user.Username,
		SessionID: sessionID,
		IsAdmin:   user.IsAdmin,
		IssuedAt:  now.Unix(),
		ExpiresAt: now.Add(s.cfg.AccessTokenTTL).Unix(),
	}
	accessToken, err := signJWT(claims, s.jwtSecret)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	return model.AuthTokensResponse{
		AccessToken:  accessToken,
		RefreshToken: rawRefresh,
		ExpiresIn:    int64(s.cfg.AccessTokenTTL.Seconds()),
		SessionID:    sessionID,
		User:         user,
	}, nil
}

func signJWT(claims Claims, secret []byte) (string, error) {
	header := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"HS256","typ":"JWT"}`))
	payloadJSON, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	payload := base64.RawURLEncoding.EncodeToString(payloadJSON)
	signingInput := header + "." + payload
	sig := base64.RawURLEncoding.EncodeToString(computeHS256(signingInput, secret))
	return signingInput + "." + sig, nil
}

func computeHS256(input string, secret []byte) []byte {
	mac := hmac.New(sha256.New, secret)
	mac.Write([]byte(input))
	return mac.Sum(nil)
}

func loadOrCreateJWTSecret(dataDir, envSecret string) ([]byte, error) {
	if strings.TrimSpace(envSecret) != "" {
		if len(envSecret) < 32 {
			return nil, errors.New("KLUDDRA_JWT_SECRET must be at least 32 characters")
		}
		return []byte(envSecret), nil
	}
	secretPath := filepath.Join(dataDir, ".jwt_secret")
	if b, err := os.ReadFile(secretPath); err == nil && len(strings.TrimSpace(string(b))) >= 32 {
		return []byte(strings.TrimSpace(string(b))), nil
	}
	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return nil, err
	}
	encoded := hex.EncodeToString(raw)
	if err := os.WriteFile(secretPath, []byte(encoded), 0o600); err != nil {
		return nil, fmt.Errorf("persist jwt secret: %w", err)
	}
	return []byte(encoded), nil
}

func generateSetupCode() (string, error) {
	const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
	raw := make([]byte, 8)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	chars := make([]byte, 8)
	for i, b := range raw {
		chars[i] = alphabet[int(b)%len(alphabet)]
	}
	return fmt.Sprintf("KLUD-%s-%s", string(chars[:4]), string(chars[4:])), nil
}

func validateCredentials(username, password string) error {
	if len(username) < 2 || len(username) > 64 {
		return fmt.Errorf("%w: username must be 2-64 characters", ErrValidation)
	}
	for _, r := range username {
		valid := (r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9') || r == '_' || r == '-' || r == '.'
		if !valid {
			return fmt.Errorf("%w: username may only contain letters, digits, '.', '-', '_'", ErrValidation)
		}
	}
	if len(password) < 8 || len(password) > 128 {
		return fmt.Errorf("%w: password must be 8-128 characters", ErrValidation)
	}
	return nil
}

func sanitizeDeviceName(name string) string {
	name = strings.TrimSpace(name)
	if name == "" {
		return "Android"
	}
	if len(name) > 80 {
		return name[:80]
	}
	return name
}

func randomBase64URL(n int) (string, error) {
	b := make([]byte, n)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

func sha256Hex(v string) string {
	sum := sha256.Sum256([]byte(v))
	return hex.EncodeToString(sum[:])
}
