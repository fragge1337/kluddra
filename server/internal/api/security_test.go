package api_test

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"net/netip"
	"testing"
	"time"

	"github.com/fragge1337/kluddra/server/internal/api"
	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/id"
	"github.com/fragge1337/kluddra/server/internal/model"
	kluddrasync "github.com/fragge1337/kluddra/server/internal/sync"
)

func TestClientIPPreventsXForwardedForSpoofing(t *testing.T) {
	trusted := []netip.Prefix{
		netip.MustParsePrefix("127.0.0.1/32"),
		netip.MustParsePrefix("172.16.0.0/12"),
	}

	// Attacker sends forged leftmost X-Forwarded-For, reverse proxy appends real IP 198.51.100.77.
	req := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", nil)
	req.RemoteAddr = "172.17.0.2:41234"
	req.Header.Set("X-Forwarded-For", "1.2.3.4, 5.6.7.8, 198.51.100.77")

	got := api.ClientIP(req, trusted)
	if got != "198.51.100.77" {
		t.Fatalf("expected real rightmost non-trusted IP 198.51.100.77, got %q (spoofed!)", got)
	}

	// Untrusted direct connection: X-Forwarded-For MUST be completely ignored.
	reqDirect := httptest.NewRequest(http.MethodPost, "/api/v1/auth/login", nil)
	reqDirect.RemoteAddr = "203.0.113.55:50000"
	reqDirect.Header.Set("X-Forwarded-For", "10.0.0.1")
	if got := api.ClientIP(reqDirect, trusted); got != "203.0.113.55" {
		t.Fatalf("expected direct IP 203.0.113.55 when not from trusted proxy, got %q", got)
	}
}

func TestSecurityHardeningJWTAlgNoneContentTypeHeadersAndCrossUserMutations(t *testing.T) {
	ctx := context.Background()
	dir := t.TempDir()
	cfg := config.Config{
		DataDir:           dir,
		AccessTokenTTL:    15 * time.Minute,
		RefreshTokenTTL:   24 * time.Hour,
		Argon2MemoryKB:    32768,
		Argon2Time:        2,
		Argon2Parallelism: 1,
		MaxSyncBodyBytes:  1 << 20,
		MaxNoteBodyBytes:  256 << 10,
		MaxNoteTitleLen:   300,
		MaxItemTextLen:    300,
		TrustedProxies:    []netip.Prefix{netip.MustParsePrefix("127.0.0.1/32")},
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
	srv := api.NewServer(cfg, store, authSvc, syncSvc, slog.New(slog.NewTextHandler(io.Discard, nil)))
	ts := httptest.NewServer(srv.Handler())
	defer ts.Close()

	// 1. Security headers present on responses
	resp, err := http.Get(ts.URL + "/api/v1/server-info")
	if err != nil {
		t.Fatalf("server-info: %v", err)
	}
	_ = resp.Body.Close()
	if resp.Header.Get("Content-Security-Policy") == "" ||
		resp.Header.Get("X-Frame-Options") != "DENY" ||
		resp.Header.Get("X-Content-Type-Options") != "nosniff" ||
		resp.Header.Get("Cache-Control") != "no-store" {
		t.Fatalf("missing required security headers: %+v", resp.Header)
	}

	// 2. Reject forged alg:none JWT
	noneHdr := base64.RawURLEncoding.EncodeToString([]byte(`{"alg":"none","typ":"JWT"}`))
	nonePayload := base64.RawURLEncoding.EncodeToString([]byte(`{"sub":"admin","usr":"admin","sid":"019ab340-0000-7000-8000-000000000001","adm":true,"iat":1700000000,"exp":9999999999}`))
	req, _ := http.NewRequest(http.MethodGet, ts.URL+"/api/v1/notes", nil)
	req.Header.Set("Authorization", "Bearer "+noneHdr+"."+nonePayload+".")
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusUnauthorized {
		t.Fatalf("expected 401 for alg:none JWT, got %d err=%v", resp.StatusCode, err)
	}
	_ = resp.Body.Close()

	// 3. Reject non-JSON Content-Type on state-changing endpoint
	req, _ = http.NewRequest(http.MethodPost, ts.URL+"/api/v1/auth/login", bytes.NewReader([]byte(`{"username":"a","password":"b"}`)))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err = http.DefaultClient.Do(req)
	if err != nil || resp.StatusCode != http.StatusBadRequest {
		t.Fatalf("expected 400 for form-encoded Content-Type, got %d err=%v", resp.StatusCode, err)
	}
	_ = resp.Body.Close()

	// 4. Cross-user mutation_id isolation in processed_mutations
	alice, err := authSvc.CreateUser(ctx, "alice", "StrongPass123!", true)
	if err != nil {
		t.Fatalf("create alice: %v", err)
	}
	bob, err := authSvc.CreateUser(ctx, "bob", "StrongPass456!", false)
	if err != nil {
		t.Fatalf("create bob: %v", err)
	}
	sharedMutID := id.NewUUIDv7()
	payloadA, _ := json.Marshal(map[string]any{"title": "Alice", "body": "Secret A"})
	payloadB, _ := json.Marshal(map[string]any{"title": "Bob", "body": "Secret B"})

	resA, err := syncSvc.Push(ctx, alice.ID, model.SyncPushRequest{
		ClientDevice: "PhoneA",
		Mutations: []model.SyncMutation{{
			MutationID: sharedMutID, Entity: "note", Op: "upsert", EntityID: id.NewUUIDv7(), Payload: payloadA,
		}},
	})
	if err != nil || resA.Results[0].Status != "applied" {
		t.Fatalf("alice push failed: %+v err=%v", resA, err)
	}
	resB, err := syncSvc.Push(ctx, bob.ID, model.SyncPushRequest{
		ClientDevice: "PhoneB",
		Mutations: []model.SyncMutation{{
			MutationID: sharedMutID, Entity: "note", Op: "upsert", EntityID: id.NewUUIDv7(), Payload: payloadB,
		}},
	})
	if err != nil || resB.Results[0].Status != "applied" {
		t.Fatalf("bob push with same mutation_id should succeed independently: %+v err=%v", resB, err)
	}

	// Verify Alice's retry still returns Alice's duplicate, never overwritten by Bob.
	resARetry, err := syncSvc.Push(ctx, alice.ID, model.SyncPushRequest{
		ClientDevice: "PhoneA",
		Mutations: []model.SyncMutation{{
			MutationID: sharedMutID, Entity: "note", Op: "upsert", EntityID: resA.Results[0].EntityID, Payload: payloadA,
		}},
	})
	if err != nil || resARetry.Results[0].Status != "duplicate" || resARetry.Results[0].Note.Title != "Alice" {
		t.Fatalf("alice idempotency was corrupted by bob: %+v err=%v", resARetry, err)
	}
}
