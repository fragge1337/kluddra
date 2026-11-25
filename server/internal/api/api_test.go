package api_test

import (
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"github.com/fragge1337/kluddra/server/internal/api"
	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/model"
	kluddrasync "github.com/fragge1337/kluddra/server/internal/sync"
)

func TestEndToEndAPIUserIsolationExportAndHotBackup(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	cfg := config.Config{
		DataDir:           dir,
		AccessTokenTTL:    15 * time.Minute,
		RefreshTokenTTL:   24 * time.Hour,
		BackupEnabled:     true,
		BackupRetainDays:  7,
		Argon2MemoryKB:    32768,
		Argon2Time:        2,
		Argon2Parallelism: 1,
		MaxSyncBodyBytes:  1 << 20,
		MaxNoteBodyBytes:  256 << 10,
		MaxNoteTitleLen:   300,
		MaxItemTextLen:    300,
	}

	store, err := db.Open(ctx, dir, nil)
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	defer store.CheckpointAndClose()

	authSvc, err := auth.NewService(ctx, store, cfg)
	if err != nil {
		t.Fatalf("auth init: %v", err)
	}
	syncSvc := kluddrasync.NewService(store, cfg)
	logger := slog.New(slog.NewTextHandler(io.Discard, nil))
	srv := api.NewServer(cfg, store, authSvc, syncSvc, logger)
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	// 1. Health & Server info
	resp, err := http.Get(ts.URL + "/api/v1/server-info")
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("server-info failed: resp=%v err=%v", resp, err)
	}
	var info model.ServerInfo
	_ = json.NewDecoder(resp.Body).Decode(&info)
	_ = resp.Body.Close()
	if !info.SetupRequired {
		t.Fatal("expected setup_required=true")
	}

	// 2. Initial setup
	setupBody, _ := json.Marshal(map[string]string{
		"setup_code":  authSvc.SetupCode(),
		"username":    "alice",
		"password":    "StrongPass123!",
		"device_name": "Pixel 9",
	})
	resp, err = http.Post(ts.URL+"/api/v1/auth/setup", "application/json", bytes.NewReader(setupBody))
	if err != nil || resp.StatusCode != http.StatusCreated {
		t.Fatalf("setup failed: status=%d err=%v", resp.StatusCode, err)
	}
	var aliceAuth model.AuthTokensResponse
	_ = json.NewDecoder(resp.Body).Decode(&aliceAuth)
	_ = resp.Body.Close()

	// 3. Create locked note as Alice
	notePayload := []byte(`{"title":"Koder","body":"Portkod 4455","is_pinned":true,"is_read_only":true}`)
	req, _ := http.NewRequest(http.MethodPost, ts.URL+"/api/v1/notes", bytes.NewReader(notePayload))
	req.Header.Set("Authorization", "Bearer "+aliceAuth.AccessToken)
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusCreated {
		t.Fatalf("create note failed: %v", err)
	}
	var createdNote model.Note
	_ = json.NewDecoder(resp.Body).Decode(&createdNote)
	_ = resp.Body.Close()

	// 4. Editing locked note via PATCH returns 423 Locked
	req, _ = http.NewRequest(http.MethodPatch, ts.URL+"/api/v1/notes/"+createdNote.ID, bytes.NewReader([]byte(`{"body":"changed"}`)))
	req.Header.Set("Authorization", "Bearer "+aliceAuth.AccessToken)
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusLocked {
		t.Fatalf("expected 423 Locked, got %d (err=%v)", resp.StatusCode, err)
	}
	_ = resp.Body.Close()

	// 5. Create second user Bob and verify strict isolation
	if _, err := authSvc.CreateUser(ctx, "bob", "BobStrong123!", false); err != nil {
		t.Fatalf("create bob: %v", err)
	}
	bobAuth, err := authSvc.Login(ctx, "bob", "BobStrong123!", "Tab S9")
	if err != nil {
		t.Fatalf("bob login: %v", err)
	}
	req, _ = http.NewRequest(http.MethodGet, ts.URL+"/api/v1/notes/"+createdNote.ID, nil)
	req.Header.Set("Authorization", "Bearer "+bobAuth.AccessToken)
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusNotFound {
		t.Fatalf("expected 404 for cross-user note access, got %d", resp.StatusCode)
	}
	_ = resp.Body.Close()

	// 6. Export Markdown ZIP
	req, _ = http.NewRequest(http.MethodGet, ts.URL+"/api/v1/export?format=markdown", nil)
	req.Header.Set("Authorization", "Bearer "+aliceAuth.AccessToken)
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusOK {
		t.Fatalf("export markdown failed: %v", err)
	}
	zipBytes, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()
	zr, err := zip.NewReader(bytes.NewReader(zipBytes), int64(len(zipBytes)))
	if err != nil || len(zr.File) < 2 {
		t.Fatalf("expected valid zip with note + shopping-list.md, err=%v count=%d", err, len(zr.File))
	}

	// 7. Hot SQLite backup via VACUUM INTO
	backupPath, err := store.Backup(ctx, filepath.Join(dir, "backups"))
	if err != nil || backupPath == "" {
		t.Fatalf("online hot backup failed: %v", err)
	}
}
