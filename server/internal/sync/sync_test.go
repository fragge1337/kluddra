package sync_test

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/fragge1337/kluddra/server/internal/auth"
	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/id"
	"github.com/fragge1337/kluddra/server/internal/model"
	kluddrasync "github.com/fragge1337/kluddra/server/internal/sync"
)

func setupSyncEnv(t *testing.T) (*db.Store, *kluddrasync.Service, model.User) {
	t.Helper()
	ctx := context.Background()
	dir := t.TempDir()
	cfg := config.Config{
		DataDir:           dir,
		AccessTokenTTL:    15 * time.Minute,
		RefreshTokenTTL:   24 * time.Hour,
		Argon2MemoryKB:    32768,
		Argon2Time:        2,
		Argon2Parallelism: 1,
		MaxNoteBodyBytes:  256 << 10,
		MaxNoteTitleLen:   300,
		MaxItemTextLen:    300,
	}
	store, err := db.Open(ctx, dir, nil)
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { _ = store.CheckpointAndClose() })

	authSvc, err := auth.NewService(ctx, store, cfg)
	if err != nil {
		t.Fatalf("auth init: %v", err)
	}
	user, err := authSvc.CreateUser(ctx, "tester", "password123", true)
	if err != nil {
		t.Fatalf("create user: %v", err)
	}
	return store, kluddrasync.NewService(store, cfg), user
}

func mustRaw(t *testing.T, v any) json.RawMessage {
	t.Helper()
	b, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal: %v", err)
	}
	return b
}

func TestOfflineConflictCreatesConflictCopyZeroDataLoss(t *testing.T) {
	ctx := context.Background()
	_, svc, user := setupSyncEnv(t)

	noteID := id.NewUUIDv7()
	// Device A creates note v1
	res1, err := svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Pixel 9",
		Mutations: []model.SyncMutation{{
			MutationID:  id.NewUUIDv7(),
			Entity:      "note",
			Op:          "upsert",
			EntityID:    noteID,
			BaseVersion: 0,
			Payload:     mustRaw(t, map[string]any{"title": "Resa", "body": "Pass, biljett"}),
		}},
	})
	if err != nil || res1.Results[0].Status != "applied" {
		t.Fatalf("initial create failed: %+v err=%v", res1, err)
	}

	// Device A edits offline and pushes first -> v2
	_, err = svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Pixel 9",
		Mutations: []model.SyncMutation{{
			MutationID:  id.NewUUIDv7(),
			Entity:      "note",
			Op:          "upsert",
			EntityID:    noteID,
			BaseVersion: 1,
			Payload:     mustRaw(t, map[string]any{"title": "Resa", "body": "Pass, biljett, laddare"}),
		}},
	})
	if err != nil {
		t.Fatalf("device A update failed: %v", err)
	}

	// Device B edited from base_version=1 offline and pushes later -> conflict copy created!
	resB, err := svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Galaxy Tab",
		Mutations: []model.SyncMutation{{
			MutationID:  id.NewUUIDv7(),
			Entity:      "note",
			Op:          "upsert",
			EntityID:    noteID,
			BaseVersion: 1,
			Payload:     mustRaw(t, map[string]any{"title": "Resa", "body": "Pass, biljett, solglasögon"}),
		}},
	})
	if err != nil {
		t.Fatalf("device B push failed: %v", err)
	}
	if resB.Results[0].Status != "conflict_copy_created" || resB.Results[0].ConflictCopy == nil {
		t.Fatalf("expected conflict_copy_created, got %+v", resB.Results[0])
	}

	notes, err := svc.ListNotes(ctx, user.ID, false)
	if err != nil || len(notes) != 2 {
		t.Fatalf("expected 2 preserved notes, got %d (err=%v)", len(notes), err)
	}
}

func TestReadOnlyProtectionEnforcedOnAPIAndOfflineSync(t *testing.T) {
	ctx := context.Background()
	_, svc, user := setupSyncEnv(t)

	note, err := svc.CreateNote(ctx, user.ID, "", "Wi-Fi", "Lösenord: 1234", "pistachio", true, true)
	if err != nil {
		t.Fatalf("create locked note: %v", err)
	}

	// Direct CRUD edit must be blocked with ErrNoteReadOnly.
	newBody := "hacked"
	if _, err := svc.UpdateNote(ctx, user.ID, note.ID, nil, &newBody, nil, nil); !errors.Is(err, kluddrasync.ErrNoteReadOnly) {
		t.Fatalf("expected ErrNoteReadOnly, got %v", err)
	}

	// Offline edit against locked note keeps locked original intact and creates conflict copy.
	res, err := svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Old Phone",
		Mutations: []model.SyncMutation{{
			MutationID:  id.NewUUIDv7(),
			Entity:      "note",
			Op:          "upsert",
			EntityID:    note.ID,
			BaseVersion: 1,
			Payload:     mustRaw(t, map[string]any{"title": "Wi-Fi", "body": "Ny text offline"}),
		}},
	})
	if err != nil || res.Results[0].Status != "conflict_copy_created" {
		t.Fatalf("expected conflict copy when editing locked note offline, got %+v err=%v", res, err)
	}

	orig, _ := svc.GetNote(ctx, user.ID, note.ID)
	if orig.Body != "Lösenord: 1234" || !orig.IsReadOnly {
		t.Fatalf("locked original was modified: %+v", orig)
	}
}

func TestEditWinsOverTrashAndIdempotentRetry(t *testing.T) {
	ctx := context.Background()
	_, svc, user := setupSyncEnv(t)

	note, err := svc.CreateNote(ctx, user.ID, "", "Idé", "Start", "lavender", false, false)
	if err != nil {
		t.Fatalf("create: %v", err)
	}
	if err := svc.DeleteNote(ctx, user.ID, note.ID, false); err != nil {
		t.Fatalf("trash: %v", err)
	}

	mutID := id.NewUUIDv7()
	req := model.SyncPushRequest{
		ClientDevice: "Pixel",
		Mutations: []model.SyncMutation{{
			MutationID:  mutID,
			Entity:      "note",
			Op:          "upsert",
			EntityID:    note.ID,
			BaseVersion: 2,
			Payload:     mustRaw(t, map[string]any{"title": "Idé", "body": "Viktig uppdatering"}),
		}},
	}
	r1, err := svc.Push(ctx, user.ID, req)
	if err != nil || r1.Results[0].Note == nil || r1.Results[0].Note.TrashedAt != nil {
		t.Fatalf("expected edit to restore trashed note, got %+v err=%v", r1, err)
	}

	// Retry same mutation_id -> duplicate, no version bump.
	r2, err := svc.Push(ctx, user.ID, req)
	if err != nil || r2.Results[0].Status != "duplicate" {
		t.Fatalf("expected duplicate on idempotent retry, got %+v err=%v", r2, err)
	}
}

func TestShoppingListClearAllDoesNotDeleteOfflineNewItem(t *testing.T) {
	ctx := context.Background()
	_, svc, user := setupSyncEnv(t)

	itemA, err := svc.UpsertShoppingItem(ctx, user.ID, "", "Äpplen", true, "a0")
	if err != nil {
		t.Fatalf("create itemA: %v", err)
	}

	// Device B adds Mjölk offline, Device A clears only known itemA.
	itemB := id.NewUUIDv7()
	_, err = svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Phone B",
		Mutations: []model.SyncMutation{{
			MutationID: id.NewUUIDv7(),
			Entity:     "shopping_item",
			Op:         "upsert",
			EntityID:   itemB,
			Payload:    mustRaw(t, map[string]any{"text": "Mjölk", "is_checked": false, "sort_order": "a1"}),
		}},
	})
	if err != nil {
		t.Fatalf("push itemB: %v", err)
	}

	_, err = svc.Push(ctx, user.ID, model.SyncPushRequest{
		ClientDevice: "Phone A",
		Mutations: []model.SyncMutation{{
			MutationID: id.NewUUIDv7(),
			Entity:     "shopping_item",
			Op:         "delete",
			EntityID:   itemA.ID,
		}},
	})
	if err != nil {
		t.Fatalf("delete itemA: %v", err)
	}

	items, err := svc.ListShoppingItems(ctx, user.ID)
	if err != nil || len(items) != 1 || items[0].Text != "Mjölk" {
		t.Fatalf("expected only Mjölk to remain, got %+v err=%v", items, err)
	}
}
