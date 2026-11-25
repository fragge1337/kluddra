package api

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/export"
	"github.com/fragge1337/kluddra/server/internal/model"
	kluddrasync "github.com/fragge1337/kluddra/server/internal/sync"
)

type claimsCtxKey struct{}

// Server wires HTTP routes, middleware, auth, sync, and export services.
type Server struct {
	cfg         config.Config
	store       *db.Store
	authSvc     *auth.Service
	syncSvc     *kluddrasync.Service
	logger      *slog.Logger
	authLimiter *RateLimiter
}

func NewServer(cfg config.Config, store *db.Store, authSvc *auth.Service, syncSvc *kluddrasync.Service, logger *slog.Logger) *Server {
	return &Server{
		cfg:         cfg,
		store:       store,
		authSvc:     authSvc,
		syncSvc:     syncSvc,
		logger:      logger,
		authLimiter: NewRateLimiter(10, 8), // Burst 8, refill 10/min per IP+user
	}
}

func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	// Public operational endpoints
	mux.HandleFunc("GET /api/v1/health", s.handleHealth)
	mux.HandleFunc("GET /api/v1/server-info", s.handleServerInfo)

	// Authentication (rate-limited)
	mux.HandleFunc("POST /api/v1/auth/setup", s.handleSetup)
	mux.HandleFunc("POST /api/v1/auth/login", s.handleLogin)
	mux.HandleFunc("POST /api/v1/auth/refresh", s.handleRefresh)
	mux.HandleFunc("POST /api/v1/auth/logout", s.requireAuth(s.handleLogout))
	mux.HandleFunc("GET /api/v1/auth/sessions", s.requireAuth(s.handleListSessions))
	mux.HandleFunc("DELETE /api/v1/auth/sessions/{id}", s.requireAuth(s.handleRevokeSession))

	// Offline-first Sync API
	mux.HandleFunc("GET /api/v1/sync", s.requireAuth(s.handleSyncPull))
	mux.HandleFunc("POST /api/v1/sync", s.requireAuth(s.handleSyncPush))

	// Standard REST CRUD for Notes (client-agnostic)
	mux.HandleFunc("GET /api/v1/notes", s.requireAuth(s.handleListNotes))
	mux.HandleFunc("POST /api/v1/notes", s.requireAuth(s.handleCreateNote))
	mux.HandleFunc("GET /api/v1/notes/{id}", s.requireAuth(s.handleGetNote))
	mux.HandleFunc("PATCH /api/v1/notes/{id}", s.requireAuth(s.handleUpdateNote))
	mux.HandleFunc("PUT /api/v1/notes/{id}/lock", s.requireAuth(s.handleSetNoteLock))
	mux.HandleFunc("DELETE /api/v1/notes/{id}", s.requireAuth(s.handleDeleteNote))

	// Standard REST CRUD for Shopping List
	mux.HandleFunc("GET /api/v1/shopping-items", s.requireAuth(s.handleListShoppingItems))
	mux.HandleFunc("POST /api/v1/shopping-items", s.requireAuth(s.handleCreateShoppingItem))
	mux.HandleFunc("PATCH /api/v1/shopping-items/{id}", s.requireAuth(s.handleUpdateShoppingItem))
	mux.HandleFunc("DELETE /api/v1/shopping-items/{id}", s.requireAuth(s.handleDeleteShoppingItem))

	// Data Portability & Admin Backup
	mux.HandleFunc("GET /api/v1/export", s.requireAuth(s.handleExport))
	mux.HandleFunc("POST /api/v1/admin/backup", s.requireAuth(s.handleAdminBackup))

	return s.withSecurityHeaders(s.withSafeLogging(mux))
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	if err := s.store.DB.PingContext(r.Context()); err != nil {
		writeError(w, http.StatusServiceUnavailable, "DB_UNAVAILABLE", "database unavailable")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

func (s *Server) handleServerInfo(w http.ResponseWriter, r *http.Request) {
	setupReq, err := s.authSvc.IsSetupRequired(r.Context())
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to read server status")
		return
	}
	writeJSON(w, http.StatusOK, model.ServerInfo{
		APIVersion:    config.APIVersion,
		ServerVersion: config.Version,
		InstanceID:    s.store.InstanceID,
		SetupRequired: setupReq,
	})
}

func (s *Server) handleSetup(w http.ResponseWriter, r *http.Request) {
	if !s.allowAuthRate(r, "setup") {
		w.Header().Set("Retry-After", "60")
		writeError(w, http.StatusTooManyRequests, "RATE_LIMITED", "too many setup attempts, try again later")
		return
	}

	var req struct {
		SetupCode  string `json:"setup_code"`
		Username   string `json:"username"`
		Password   string `json:"password"`
		DeviceName string `json:"device_name"`
	}
	if err := decodeJSON(w, r, 16<<10, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}

	resp, err := s.authSvc.CompleteSetup(r.Context(), req.SetupCode, req.Username, req.Password, req.DeviceName)
	if err != nil {
		switch {
		case errors.Is(err, auth.ErrSetupNotAllowed):
			writeError(w, http.StatusForbidden, "SETUP_DISABLED", "initial setup has already been completed")
		case errors.Is(err, auth.ErrInvalidSetupCode):
			writeError(w, http.StatusUnauthorized, "INVALID_SETUP_CODE", "invalid setup code")
		case errors.Is(err, auth.ErrValidation):
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		default:
			writeError(w, http.StatusInternalServerError, "SETUP_FAILED", "could not complete setup")
		}
		return
	}
	writeJSON(w, http.StatusCreated, resp)
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req struct {
		Username   string `json:"username"`
		Password   string `json:"password"`
		DeviceName string `json:"device_name"`
	}
	if err := decodeJSON(w, r, 16<<10, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}

	if !s.allowAuthRate(r, "login:"+strings.ToLower(strings.TrimSpace(req.Username))) {
		w.Header().Set("Retry-After", "60")
		writeError(w, http.StatusTooManyRequests, "RATE_LIMITED", "too many login attempts, try again later")
		return
	}

	resp, err := s.authSvc.Login(r.Context(), req.Username, req.Password, req.DeviceName)
	if err != nil {
		if errors.Is(err, auth.ErrInvalidCredentials) {
			writeError(w, http.StatusUnauthorized, "INVALID_CREDENTIALS", "invalid username or password")
			return
		}
		writeError(w, http.StatusInternalServerError, "LOGIN_FAILED", "login failed")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleRefresh(w http.ResponseWriter, r *http.Request) {
	if !s.allowAuthRate(r, "refresh") {
		w.Header().Set("Retry-After", "60")
		writeError(w, http.StatusTooManyRequests, "RATE_LIMITED", "too many token refresh attempts")
		return
	}

	var req struct {
		RefreshToken string `json:"refresh_token"`
		DeviceName   string `json:"device_name"`
	}
	if err := decodeJSON(w, r, 16<<10, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}

	resp, err := s.authSvc.Refresh(r.Context(), req.RefreshToken, req.DeviceName)
	if err != nil {
		if errors.Is(err, auth.ErrTokenReused) {
			writeError(w, http.StatusUnauthorized, "TOKEN_REUSED", "session revoked due to token reuse")
			return
		}
		writeError(w, http.StatusUnauthorized, "INVALID_REFRESH_TOKEN", "invalid or expired refresh token")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	if err := s.authSvc.Logout(r.Context(), claims.Sub, claims.SessionID); err != nil {
		writeError(w, http.StatusInternalServerError, "LOGOUT_FAILED", "failed to revoke session")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleListSessions(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	sessions, err := s.authSvc.ListSessions(r.Context(), claims.Sub, claims.SessionID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to list sessions")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"sessions": sessions})
}

func (s *Server) handleRevokeSession(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	targetID := r.PathValue("id")
	if err := s.authSvc.Logout(r.Context(), claims.Sub, targetID); err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to revoke session")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleSyncPull(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	sinceSeq, _ := strconv.ParseInt(r.URL.Query().Get("since"), 10, 64)
	if sinceSeq < 0 {
		sinceSeq = 0
	}
	limit, _ := strconv.Atoi(r.URL.Query().Get("limit"))

	resp, err := s.syncSvc.Pull(r.Context(), claims.Sub, sinceSeq, limit)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "SYNC_PULL_FAILED", "failed to pull changes")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleSyncPush(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req model.SyncPushRequest
	if err := decodeJSON(w, r, s.cfg.MaxSyncBodyBytes, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	resp, err := s.syncSvc.Push(r.Context(), claims.Sub, req)
	if err != nil {
		if errors.Is(err, kluddrasync.ErrInvalidInput) {
			writeError(w, http.StatusBadRequest, "INVALID_INPUT", err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, "SYNC_PUSH_FAILED", "failed to push changes")
		return
	}
	writeJSON(w, http.StatusOK, resp)
}

func (s *Server) handleListNotes(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	includeTrashed := r.URL.Query().Get("include_trashed") == "true"
	notes, err := s.syncSvc.ListNotes(r.Context(), claims.Sub, includeTrashed)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to list notes")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"notes": notes})
}

func (s *Server) handleCreateNote(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req struct {
		ID         string `json:"id"`
		Title      string `json:"title"`
		Body       string `json:"body"`
		IsPinned   bool   `json:"is_pinned"`
		IsReadOnly bool   `json:"is_read_only"`
	}
	if err := decodeJSON(w, r, int64(s.cfg.MaxNoteBodyBytes+4096), &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	note, err := s.syncSvc.CreateNote(r.Context(), claims.Sub, req.ID, req.Title, req.Body, req.IsPinned, req.IsReadOnly)
	if err != nil {
		if errors.Is(err, kluddrasync.ErrInvalidInput) {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to create note")
		return
	}
	writeJSON(w, http.StatusCreated, note)
}

func (s *Server) handleGetNote(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	note, err := s.syncSvc.GetNote(r.Context(), claims.Sub, r.PathValue("id"))
	if err != nil {
		if errors.Is(err, kluddrasync.ErrNotFound) {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "note not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to fetch note")
		return
	}
	writeJSON(w, http.StatusOK, note)
}

func (s *Server) handleUpdateNote(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req struct {
		Title    *string `json:"title"`
		Body     *string `json:"body"`
		IsPinned *bool   `json:"is_pinned"`
	}
	if err := decodeJSON(w, r, int64(s.cfg.MaxNoteBodyBytes+4096), &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	note, err := s.syncSvc.UpdateNote(r.Context(), claims.Sub, r.PathValue("id"), req.Title, req.Body, req.IsPinned)
	if err != nil {
		switch {
		case errors.Is(err, kluddrasync.ErrNotFound):
			writeError(w, http.StatusNotFound, "NOT_FOUND", "note not found")
		case errors.Is(err, kluddrasync.ErrNoteReadOnly):
			writeError(w, http.StatusLocked, "NOTE_READ_ONLY", "note is write-protected; unlock before editing")
		case errors.Is(err, kluddrasync.ErrInvalidInput):
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
		default:
			writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to update note")
		}
		return
	}
	writeJSON(w, http.StatusOK, note)
}

func (s *Server) handleSetNoteLock(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req struct {
		IsReadOnly bool `json:"is_read_only"`
	}
	if err := decodeJSON(w, r, 4096, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	note, err := s.syncSvc.SetNoteReadOnly(r.Context(), claims.Sub, r.PathValue("id"), req.IsReadOnly)
	if err != nil {
		if errors.Is(err, kluddrasync.ErrNotFound) {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "note not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to update lock state")
		return
	}
	writeJSON(w, http.StatusOK, note)
}

func (s *Server) handleDeleteNote(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	permanent := r.URL.Query().Get("permanent") == "true"
	if err := s.syncSvc.DeleteNote(r.Context(), claims.Sub, r.PathValue("id"), permanent); err != nil {
		if errors.Is(err, kluddrasync.ErrNotFound) {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "note not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to delete note")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleListShoppingItems(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	items, err := s.syncSvc.ListShoppingItems(r.Context(), claims.Sub)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to list shopping items")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"items": items})
}

func (s *Server) handleCreateShoppingItem(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req struct {
		ID        string `json:"id"`
		Text      string `json:"text"`
		IsChecked bool   `json:"is_checked"`
		SortOrder string `json:"sort_order"`
	}
	if err := decodeJSON(w, r, 8192, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	item, err := s.syncSvc.UpsertShoppingItem(r.Context(), claims.Sub, req.ID, req.Text, req.IsChecked, req.SortOrder)
	if err != nil {
		if errors.Is(err, kluddrasync.ErrInvalidInput) {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to create shopping item")
		return
	}
	writeJSON(w, http.StatusCreated, item)
}

func (s *Server) handleUpdateShoppingItem(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	var req struct {
		Text      string `json:"text"`
		IsChecked bool   `json:"is_checked"`
		SortOrder string `json:"sort_order"`
	}
	if err := decodeJSON(w, r, 8192, &req); err != nil {
		writeError(w, http.StatusBadRequest, "INVALID_JSON", err.Error())
		return
	}
	item, err := s.syncSvc.UpsertShoppingItem(r.Context(), claims.Sub, r.PathValue("id"), req.Text, req.IsChecked, req.SortOrder)
	if err != nil {
		if errors.Is(err, kluddrasync.ErrInvalidInput) {
			writeError(w, http.StatusBadRequest, "VALIDATION_ERROR", err.Error())
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to update shopping item")
		return
	}
	writeJSON(w, http.StatusOK, item)
}

func (s *Server) handleDeleteShoppingItem(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	if err := s.syncSvc.DeleteShoppingItem(r.Context(), claims.Sub, r.PathValue("id")); err != nil {
		if errors.Is(err, kluddrasync.ErrNotFound) {
			writeError(w, http.StatusNotFound, "NOT_FOUND", "shopping item not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "INTERNAL_ERROR", "failed to delete shopping item")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleExport(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	format := strings.ToLower(strings.TrimSpace(r.URL.Query().Get("format")))
	if format == "" {
		format = "json"
	}

	notes, err := s.syncSvc.ListNotes(r.Context(), claims.Sub, true)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "EXPORT_FAILED", "failed to load notes for export")
		return
	}
	items, err := s.syncSvc.ListShoppingItems(r.Context(), claims.Sub)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "EXPORT_FAILED", "failed to load shopping items for export")
		return
	}

	stamp := time.Now().UTC().Format("20060102")
	switch format {
	case "json":
		data, err := export.BuildJSON(claims.Username, notes, items)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "EXPORT_FAILED", "failed to generate JSON export")
			return
		}
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="kluddra-export-%s.json"`, stamp))
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(data)

	case "markdown", "md", "zip":
		data, err := export.BuildMarkdownZIP(notes, items)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "EXPORT_FAILED", "failed to generate Markdown archive")
			return
		}
		w.Header().Set("Content-Type", "application/zip")
		w.Header().Set("Content-Disposition", fmt.Sprintf(`attachment; filename="kluddra-notes-%s.zip"`, stamp))
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write(data)

	default:
		writeError(w, http.StatusBadRequest, "UNSUPPORTED_FORMAT", "format must be 'json' or 'markdown'")
	}
}

func (s *Server) handleAdminBackup(w http.ResponseWriter, r *http.Request) {
	claims := getClaims(r.Context())
	if !claims.IsAdmin {
		writeError(w, http.StatusForbidden, "FORBIDDEN", "administrator privileges required")
		return
	}
	backupDir := filepath.Join(s.cfg.DataDir, "backups")
	path, err := s.store.Backup(r.Context(), backupDir)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "BACKUP_FAILED", "failed to create database backup")
		return
	}
	_ = db.PruneBackups(backupDir, s.cfg.BackupRetainDays)
	writeJSON(w, http.StatusOK, map[string]string{
		"status": "ok",
		"file":   filepath.Base(path),
	})
}

func (s *Server) requireAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		authHeader := r.Header.Get("Authorization")
		if !strings.HasPrefix(authHeader, "Bearer ") {
			writeError(w, http.StatusUnauthorized, "MISSING_TOKEN", "bearer token required")
			return
		}
		token := strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
		claims, err := s.authSvc.VerifyAccessToken(r.Context(), token)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "INVALID_TOKEN", "invalid or expired access token")
			return
		}
		ctx := context.WithValue(r.Context(), claimsCtxKey{}, claims)
		next(w, r.WithContext(ctx))
	}
}

func getClaims(ctx context.Context) auth.Claims {
	c, _ := ctx.Value(claimsCtxKey{}).(auth.Claims)
	return c
}

func (s *Server) allowAuthRate(r *http.Request, scope string) bool {
	ip := ClientIP(r, s.cfg.TrustedProxies)
	return s.authLimiter.Allow(ip + "|" + scope)
}

func (s *Server) withSecurityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("X-Content-Type-Options", "nosniff")
		w.Header().Set("X-Frame-Options", "DENY")
		w.Header().Set("Referrer-Policy", "no-referrer")
		w.Header().Set("Cache-Control", "no-store")
		next.ServeHTTP(w, r)
	})
}

type statusRecorder struct {
	http.ResponseWriter
	status int
}

func (sr *statusRecorder) WriteHeader(code int) {
	sr.status = code
	sr.ResponseWriter.WriteHeader(code)
}

func (s *Server) withSafeLogging(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
		next.ServeHTTP(rec, r)
		if r.URL.Path == "/api/v1/health" {
			return
		}
		// Never log Authorization headers, request bodies, passwords, or note contents.
		s.logger.Info("http_request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", rec.status,
			"duration_ms", time.Since(start).Milliseconds(),
			"client_ip", ClientIP(r, s.cfg.TrustedProxies),
		)
	})
}

func decodeJSON(w http.ResponseWriter, r *http.Request, maxBytes int64, dst any) error {
	r.Body = http.MaxBytesReader(w, r.Body, maxBytes)
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		return errors.New("invalid JSON request body")
	}
	if err := dec.Decode(&struct{}{}); err != io.EOF {
		return errors.New("request body must contain a single JSON object")
	}
	return nil
}

func writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(v)
}

func writeError(w http.ResponseWriter, status int, code, message string) {
	writeJSON(w, status, model.APIError{Code: code, Message: message})
}
