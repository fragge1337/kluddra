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
	"unicode/utf8"

	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/id"
	"github.com/fragge1337/kluddra/server/internal/model"
	"golang.org/x/crypto/argon2"
)

const (
	maxSetupFailures      = 10
	maxAccessTokenBytes   = 4096
	maxConcurrentArgon2   = 4
	maxArgon2MemoryKB     = 262144 // 256 MiB upper safety bound
	maxArgon2Iterations   = 10
	maxArgon2Parallelism  = 8
	lockoutResetWindow    = 24 * time.Hour
	maxLockoutRows        = 10_000
)

var (
	ErrInvalidCredentials = errors.New("invalid credentials")
	ErrAccountLocked      = errors.New("too many failed login attempts")
	ErrInvalidToken       = errors.New("invalid or expired token")
	ErrTokenReused        = errors.New("refresh token reuse detected")
	ErrSetupNotAllowed    = errors.New("initial setup already completed")
	ErrSetupLocked        = errors.New("initial setup locked after too many failed attempts; restart container")
	ErrInvalidSetupCode   = errors.New("invalid setup code")
	ErrUsernameTaken      = errors.New("username already exists")
	ErrValidation         = errors.New("validation error")
)

// LockoutError carries the remaining backoff duration when a username or IP is locked.
type LockoutError struct {
	RetryAfter time.Duration
}

func (e *LockoutError) Error() string {
	return fmt.Sprintf("too many failed login attempts, retry in %ds", max(1, int(e.RetryAfter.Seconds())))
}

func (e *LockoutError) Unwrap() error {
	return ErrAccountLocked
}

type jwtHeader struct {
	Alg string `json:"alg"`
	Typ string `json:"typ"`
}

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
	argonSem  chan struct{}

	setupMu       sync.Mutex
	setupCode     string
	setupFailures int
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
		argonSem:  make(chan struct{}, maxConcurrentArgon2),
	}

	required, err := svc.IsSetupRequired(ctx)
	if err != nil {
		return nil, err
	}
	if required {
		if cfg.AdminSetupToken != "" {
			if len(cfg.AdminSetupToken) < 12 {
				return nil, errors.New("KLUDDRA_ADMIN_SETUP_TOKEN must be at least 12 characters")
			}
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
	s.setupMu.Lock()
	defer s.setupMu.Unlock()
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

// CompleteSetup atomically creates the first administrator account using the bootstrap setup code.
func (s *Service) CompleteSetup(ctx context.Context, setupCode, username, password, deviceName string) (model.AuthTokensResponse, error) {
	s.setupMu.Lock()
	defer s.setupMu.Unlock()

	if s.setupFailures >= maxSetupFailures {
		return model.AuthTokensResponse{}, ErrSetupLocked
	}
	if s.setupCode == "" {
		return model.AuthTokensResponse{}, ErrSetupNotAllowed
	}

	if subtle.ConstantTimeCompare([]byte(strings.TrimSpace(setupCode)), []byte(s.setupCode)) != 1 {
		s.setupFailures++
		if s.setupFailures >= maxSetupFailures {
			s.setupCode = ""
			return model.AuthTokensResponse{}, ErrSetupLocked
		}
		return model.AuthTokensResponse{}, ErrInvalidSetupCode
	}

	username = strings.TrimSpace(username)
	if err := validateCredentials(username, password); err != nil {
		return model.AuthTokensResponse{}, err
	}

	hash, err := s.HashPassword(ctx, password)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	now := time.Now().UnixMilli()
	user := model.User{
		ID:           id.NewUUIDv7(),
		Username:     username,
		PasswordHash: hash,
		IsAdmin:      true,
		CreatedAt:    now,
		UpdatedAt:    now,
	}

	err = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		var count int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM users`).Scan(&count); err != nil {
			return err
		}
		if count > 0 {
			return ErrSetupNotAllowed
		}
		_, err := tx.ExecContext(ctx, `
			INSERT INTO users (id, username, password_hash, is_admin, sync_seq, created_at, updated_at)
			VALUES (?, ?, ?, 1, 0, ?, ?)
		`, user.ID, user.Username, user.PasswordHash, user.CreatedAt, user.UpdatedAt)
		return err
	})
	if err != nil {
		return model.AuthTokensResponse{}, err
	}

	// Permanently wipe bootstrap setup code once first admin exists.
	s.setupCode = ""
	return s.issueSessionTokens(ctx, user, deviceName, "")
}

// CreateUser creates a new user account with an Argon2id-hashed password.
func (s *Service) CreateUser(ctx context.Context, username, password string, isAdmin bool) (model.User, error) {
	username = strings.TrimSpace(username)
	if err := validateCredentials(username, password); err != nil {
		return model.User{}, err
	}

	hash, err := s.HashPassword(ctx, password)
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
	if len(username) == 0 || len(username) > 64 || len(password) == 0 || len(password) > 128 {
		return model.AuthTokensResponse{}, ErrInvalidCredentials
	}

	var user model.User
	var isAdmin int
	err := s.store.DB.QueryRowContext(ctx, `
		SELECT id, username, password_hash, is_admin, sync_seq, created_at, updated_at
		FROM users WHERE username = ?
	`, username).Scan(&user.ID, &user.Username, &user.PasswordHash, &isAdmin, &user.SyncSeq, &user.CreatedAt, &user.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		// Burn comparable Argon2id time to prevent username enumeration via timing.
		_, _ = s.HashPassword(ctx, "dummy-password-for-timing-equalization")
		return model.AuthTokensResponse{}, ErrInvalidCredentials
	}
	if err != nil {
		return model.AuthTokensResponse{}, err
	}
	user.IsAdmin = isAdmin == 1

	ok, err := s.verifyPasswordBounded(ctx, password, user.PasswordHash)
	if err != nil || !ok {
		return model.AuthTokensResponse{}, ErrInvalidCredentials
	}

	return s.issueSessionTokens(ctx, user, deviceName, "")
}

// Refresh atomically rotates a refresh token inside a single write transaction,
// preventing TOCTOU races and revoking the entire token family if reuse is detected.
func (s *Service) Refresh(ctx context.Context, rawRefreshToken, deviceName string) (model.AuthTokensResponse, error) {
	rawRefreshToken = strings.TrimSpace(rawRefreshToken)
	if len(rawRefreshToken) < 20 || len(rawRefreshToken) > 256 {
		return model.AuthTokensResponse{}, ErrInvalidToken
	}

	tokenHash := sha256Hex(rawRefreshToken)
	rawReplacement, err := randomBase64URL(32)
	if err != nil {
		return model.AuthTokensResponse{}, err
	}
	now := time.Now()
	nowMs := now.UnixMilli()
	expiresMs := now.Add(s.cfg.RefreshTokenTTL).UnixMilli()
	newSessionID := id.NewUUIDv7()

	var (
		user        model.User
		tokenReused bool
	)

	err = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		var (
			sessionID   string
			userID      string
			tokenFamily string
			storedDev   string
			expiresAt   int64
			revokedAt   sql.NullInt64
		)

		scanErr := tx.QueryRowContext(ctx, `
			SELECT id, user_id, token_family, device_name, expires_at, revoked_at
			FROM sessions WHERE token_hash = ?
		`, tokenHash).Scan(&sessionID, &userID, &tokenFamily, &storedDev, &expiresAt, &revokedAt)
		if errors.Is(scanErr, sql.ErrNoRows) {
			return ErrInvalidToken
		}
		if scanErr != nil {
			return scanErr
		}

		// Reuse detection: a previously rotated or revoked token was replayed!
		// Revoke the entire family and COMMIT that revocation before returning ErrTokenReused.
		if revokedAt.Valid {
			if _, e := tx.ExecContext(ctx, `
				UPDATE sessions SET revoked_at = COALESCE(revoked_at, ?)
				WHERE token_family = ?
			`, nowMs, tokenFamily); e != nil {
				return e
			}
			tokenReused = true
			return nil
		}

		if nowMs >= expiresAt {
			return ErrInvalidToken
		}

		var isAdmin int
		if uerr := tx.QueryRowContext(ctx, `
			SELECT id, username, is_admin, sync_seq, created_at, updated_at
			FROM users WHERE id = ?
		`, userID).Scan(&user.ID, &user.Username, &isAdmin, &user.SyncSeq, &user.CreatedAt, &user.UpdatedAt); uerr != nil {
			return ErrInvalidToken
		}
		user.IsAdmin = isAdmin == 1

		// Revoke the used session row and insert rotated replacement atomically.
		if _, e := tx.ExecContext(ctx, `
			UPDATE sessions SET revoked_at = ?, last_used_at = ? WHERE id = ?
		`, nowMs, nowMs, sessionID); e != nil {
			return e
		}

		finalDev := sanitizeDeviceName(deviceName)
		if strings.TrimSpace(deviceName) == "" {
			finalDev = storedDev
		}
		_, e := tx.ExecContext(ctx, `
			INSERT INTO sessions (id, user_id, token_hash, token_family, device_name, created_at, last_used_at, expires_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		`, newSessionID, user.ID, sha256Hex(rawReplacement), tokenFamily, finalDev, nowMs, nowMs, expiresMs)
		return e
	})
	if err != nil {
		return model.AuthTokensResponse{}, err
	}
	if tokenReused {
		return model.AuthTokensResponse{}, ErrTokenReused
	}

	claims := Claims{
		Sub:       user.ID,
		Username:  user.Username,
		SessionID: newSessionID,
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
		RefreshToken: rawReplacement,
		ExpiresIn:    int64(s.cfg.AccessTokenTTL.Seconds()),
		SessionID:    newSessionID,
		User:         user,
	}, nil
}

// Logout revokes a specific session belonging to userID.
func (s *Service) Logout(ctx context.Context, userID, sessionID string) error {
	if !id.IsValidUUID(sessionID) {
		return nil
	}
	now := time.Now().UnixMilli()
	return s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		_, err := tx.ExecContext(ctx, `
			UPDATE sessions SET revoked_at = COALESCE(revoked_at, ?)
			WHERE id = ? AND user_id = ?
		`, now, sessionID, userID)
		return err
	})
}

// RevokeOtherSessions revokes all active sessions for userID except currentSessionID.
func (s *Service) RevokeOtherSessions(ctx context.Context, userID, currentSessionID string) (int64, error) {
	now := time.Now().UnixMilli()
	var revoked int64
	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE sessions
			SET revoked_at = ?
			WHERE user_id = ? AND id != ? AND revoked_at IS NULL
		`, now, userID, currentSessionID)
		if err != nil {
			return err
		}
		n, err := res.RowsAffected()
		if err != nil {
			return err
		}
		revoked = n
		return nil
	})
	return revoked, err
}

// CheckLoginLockout verifies neither the target username nor client IP is currently under
// persistent progressive backoff. Runs BEFORE Argon2id so locked requests cost zero CPU/RAM.
func (s *Service) CheckLoginLockout(ctx context.Context, clientIP, username string) error {
	nowMs := time.Now().UnixMilli()
	normUser := strings.ToLower(strings.TrimSpace(username))
	normIP := strings.TrimSpace(clientIP)

	var maxLockedUntil int64
	checkScope := func(scope, key string) error {
		if key == "" {
			return nil
		}
		var lockedUntil int64
		err := s.store.DB.QueryRowContext(ctx, `
			SELECT locked_until FROM login_lockouts WHERE scope = ? AND key = ?
		`, scope, key).Scan(&lockedUntil)
		if errors.Is(err, sql.ErrNoRows) {
			return nil
		}
		if err != nil {
			return err
		}
		if lockedUntil > maxLockedUntil {
			maxLockedUntil = lockedUntil
		}
		return nil
	}

	if err := checkScope("user", normUser); err != nil {
		return err
	}
	if err := checkScope("ip", normIP); err != nil {
		return err
	}

	if maxLockedUntil > nowMs {
		rem := time.Duration(maxLockedUntil-nowMs) * time.Millisecond
		if rem < time.Second {
			rem = time.Second
		}
		return &LockoutError{RetryAfter: rem}
	}
	return nil
}

// RecordLoginFailure increments persistent failure counters for both username and client IP
// and applies exponential backoff (5->30s, 8->5m, 11->30m, 15+->60m). Recorded identically
// for existing and non-existing usernames so attackers cannot enumerate valid accounts.
func (s *Service) RecordLoginFailure(ctx context.Context, clientIP, username string) (int, time.Duration) {
	now := time.Now()
	nowMs := now.UnixMilli()
	cutoffMs := now.Add(-lockoutResetWindow).UnixMilli()
	normUser := strings.ToLower(strings.TrimSpace(username))
	normIP := strings.TrimSpace(clientIP)

	var (
		maxFailed int
		maxLock   time.Duration
	)

	_ = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		// Prune expired lockout rows and enforce hard row ceiling against disk-fill spraying.
		_, _ = tx.ExecContext(ctx, `DELETE FROM login_lockouts WHERE last_failed_at < ?`, cutoffMs)
		var totalRows int
		if err := tx.QueryRowContext(ctx, `SELECT COUNT(*) FROM login_lockouts`).Scan(&totalRows); err == nil && totalRows >= maxLockoutRows {
			_, _ = tx.ExecContext(ctx, `
				DELETE FROM login_lockouts
				WHERE rowid IN (
					SELECT rowid FROM login_lockouts ORDER BY last_failed_at ASC LIMIT 500
				)
			`)
		}

		bump := func(scope, key string) error {
			if key == "" {
				return nil
			}
			var failedCount int
			var lastFailedAt int64
			err := tx.QueryRowContext(ctx, `
				SELECT failed_count, last_failed_at FROM login_lockouts WHERE scope = ? AND key = ?
			`, scope, key).Scan(&failedCount, &lastFailedAt)
			if errors.Is(err, sql.ErrNoRows) || lastFailedAt < cutoffMs {
				failedCount = 0
			} else if err != nil {
				return err
			}

			failedCount++
			lockDur := lockoutDurationForFailures(failedCount)
			lockedUntil := int64(0)
			if lockDur > 0 {
				lockedUntil = now.Add(lockDur).UnixMilli()
			}

			_, err = tx.ExecContext(ctx, `
				INSERT INTO login_lockouts (scope, key, failed_count, locked_until, last_failed_at)
				VALUES (?, ?, ?, ?, ?)
				ON CONFLICT(scope, key) DO UPDATE SET
					failed_count = excluded.failed_count,
					locked_until = excluded.locked_until,
					last_failed_at = excluded.last_failed_at
			`, scope, key, failedCount, lockedUntil, nowMs)
			if err != nil {
				return err
			}
			if failedCount > maxFailed {
				maxFailed = failedCount
			}
			if lockDur > maxLock {
				maxLock = lockDur
			}
			return nil
		}

		if err := bump("user", normUser); err != nil {
			return err
		}
		return bump("ip", normIP)
	})

	return maxFailed, maxLock
}

// ClearLoginFailures resets failure counters for both username and IP after a valid login.
func (s *Service) ClearLoginFailures(ctx context.Context, clientIP, username string) {
	normUser := strings.ToLower(strings.TrimSpace(username))
	normIP := strings.TrimSpace(clientIP)
	_ = s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		if normUser != "" {
			_, _ = tx.ExecContext(ctx, `DELETE FROM login_lockouts WHERE scope = 'user' AND key = ?`, normUser)
		}
		if normIP != "" {
			_, _ = tx.ExecContext(ctx, `DELETE FROM login_lockouts WHERE scope = 'ip' AND key = ?`, normIP)
		}
		return nil
	})
}

func lockoutDurationForFailures(failedCount int) time.Duration {
	switch {
	case failedCount >= 15:
		return 60 * time.Minute
	case failedCount >= 11:
		return 30 * time.Minute
	case failedCount >= 8:
		return 5 * time.Minute
	case failedCount >= 5:
		return 30 * time.Second
	default:
		return 0
	}
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

// VerifyAccessToken validates token size, JWT header alg/typ, HMAC-SHA256 signature,
// expiry, iat sanity, and verifies the backing session has not been revoked.
func (s *Service) VerifyAccessToken(ctx context.Context, token string) (Claims, error) {
	if len(token) == 0 || len(token) > maxAccessTokenBytes {
		return Claims{}, ErrInvalidToken
	}

	parts := strings.Split(token, ".")
	if len(parts) != 3 {
		return Claims{}, ErrInvalidToken
	}

	headerBytes, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return Claims{}, ErrInvalidToken
	}
	var hdr jwtHeader
	if err := json.Unmarshal(headerBytes, &hdr); err != nil || hdr.Alg != "HS256" || hdr.Typ != "JWT" {
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

	nowSec := time.Now().Unix()
	if nowSec >= claims.ExpiresAt || claims.IssuedAt > nowSec+60 || claims.Sub == "" || !id.IsValidUUID(claims.SessionID) {
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

// HashPassword produces an Argon2id PHC-encoded password hash, bounded by a concurrency semaphore.
func (s *Service) HashPassword(ctx context.Context, password string) (string, error) {
	select {
	case s.argonSem <- struct{}{}:
		defer func() { <-s.argonSem }()
	case <-ctx.Done():
		return "", ctx.Err()
	}

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

func (s *Service) verifyPasswordBounded(ctx context.Context, password, encoded string) (bool, error) {
	select {
	case s.argonSem <- struct{}{}:
		defer func() { <-s.argonSem }()
	case <-ctx.Done():
		return false, ctx.Err()
	}
	return VerifyPassword(password, encoded)
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
	if memory < 8192 || memory > maxArgon2MemoryKB || iterations < 1 || iterations > maxArgon2Iterations || parallelism < 1 || parallelism > maxArgon2Parallelism {
		return false, errors.New("argon2 params out of safe bounds")
	}

	salt, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil || len(salt) < 8 || len(salt) > 64 {
		return false, errors.New("invalid salt")
	}
	expectedHash, err := base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil || len(expectedHash) < 16 || len(expectedHash) > 64 {
		return false, errors.New("invalid hash")
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
	raw := make([]byte, 12)
	if _, err := rand.Read(raw); err != nil {
		return "", err
	}
	chars := make([]byte, 12)
	for i, b := range raw {
		chars[i] = alphabet[int(b)%len(alphabet)]
	}
	// 60 bits of entropy (32^12) -> unguessable even without rate limiting.
	return fmt.Sprintf("KLUD-%s-%s-%s", string(chars[:4]), string(chars[4:8]), string(chars[8:12])), nil
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
	return truncateRunes(name, 80)
}

func truncateRunes(s string, max int) string {
	if utf8.RuneCountInString(s) <= max {
		return s
	}
	runes := []rune(s)
	return string(runes[:max])
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
