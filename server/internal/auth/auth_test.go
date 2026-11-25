package auth_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
)

func testConfig(dir string) config.Config {
	return config.Config{
		DataDir:           dir,
		AccessTokenTTL:    15 * time.Minute,
		RefreshTokenTTL:   30 * 24 * time.Hour,
		Argon2MemoryKB:    32768,
		Argon2Time:        2,
		Argon2Parallelism: 1,
	}
}

func TestSetupLoginRefreshAndReuseDetection(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	cfg := testConfig(dir)

	store, err := db.Open(ctx, dir, nil)
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	defer store.CheckpointAndClose()

	svc, err := auth.NewService(ctx, store, cfg)
	if err != nil {
		t.Fatalf("new auth service: %v", err)
	}

	req, err := svc.IsSetupRequired(ctx)
	if err != nil || !req {
		t.Fatalf("expected setup required=true, got %v (err=%v)", req, err)
	}
	code := svc.SetupCode()
	if code == "" {
		t.Fatal("expected generated setup code")
	}

	// Wrong setup code must fail.
	if _, err := svc.CompleteSetup(ctx, "WRONG-CODE", "alice", "supersecret123", "Pixel 9"); !errors.Is(err, auth.ErrInvalidSetupCode) {
		t.Fatalf("expected ErrInvalidSetupCode, got %v", err)
	}

	// Valid setup succeeds and disables further setup.
	tokens, err := svc.CompleteSetup(ctx, code, "alice", "supersecret123", "Pixel 9")
	if err != nil {
		t.Fatalf("complete setup: %v", err)
	}
	if tokens.AccessToken == "" || tokens.RefreshToken == "" {
		t.Fatal("expected non-empty access and refresh tokens")
	}

	if _, err := svc.CompleteSetup(ctx, code, "bob", "supersecret123", "Pixel 8"); !errors.Is(err, auth.ErrSetupNotAllowed) {
		t.Fatalf("expected ErrSetupNotAllowed on second setup, got %v", err)
	}

	// Verify access token.
	claims, err := svc.VerifyAccessToken(ctx, tokens.AccessToken)
	if err != nil || claims.Username != "alice" || !claims.IsAdmin {
		t.Fatalf("unexpected claims: %+v err=%v", claims, err)
	}

	// Rotate refresh token.
	rotated, err := svc.Refresh(ctx, tokens.RefreshToken, "Pixel 9")
	if err != nil {
		t.Fatalf("refresh failed: %v", err)
	}
	if rotated.RefreshToken == tokens.RefreshToken {
		t.Fatal("expected refresh token rotation")
	}

	// Reusing old refresh token triggers reuse detection and revokes the whole family!
	if _, err := svc.Refresh(ctx, tokens.RefreshToken, "Pixel 9"); !errors.Is(err, auth.ErrTokenReused) {
		t.Fatalf("expected ErrTokenReused, got %v", err)
	}

	// The rotated token must now also be revoked.
	if _, err := svc.VerifyAccessToken(ctx, rotated.AccessToken); !errors.Is(err, auth.ErrInvalidToken) {
		t.Fatalf("expected rotated access token to be revoked after family compromise, got %v", err)
	}

	// Fresh login still works.
	fresh, err := svc.Login(ctx, "alice", "supersecret123", "Galaxy Tab")
	if err != nil {
		t.Fatalf("fresh login: %v", err)
	}
	sessions, err := svc.ListSessions(ctx, fresh.User.ID, fresh.SessionID)
	if err != nil || len(sessions) != 1 || !sessions[0].Current {
		t.Fatalf("unexpected sessions: %+v err=%v", sessions, err)
	}
}
