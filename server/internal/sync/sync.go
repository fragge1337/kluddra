package sync

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/fragge1337/kluddra/server/internal/config"
	"github.com/fragge1337/kluddra/server/internal/db"
	"github.com/fragge1337/kluddra/server/internal/id"
	"github.com/fragge1337/kluddra/server/internal/model"
)

var (
	ErrNotFound     = errors.New("not found")
	ErrNoteReadOnly = errors.New("note is read-only")
	ErrInvalidInput = errors.New("invalid input")
	ErrConflict     = errors.New("version conflict")
)

type Service struct {
	store *db.Store
	cfg   config.Config
}

func NewService(store *db.Store, cfg config.Config) *Service {
	return &Service{store: store, cfg: cfg}
}

// Pull returns all notes and shopping items modified after sinceSeq for userID.
func (s *Service) Pull(ctx context.Context, userID string, sinceSeq int64, limit int) (model.SyncPullResponse, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}

	var currentSeq int64
	if err := s.store.DB.QueryRowContext(ctx, `SELECT sync_seq FROM users WHERE id = ?`, userID).Scan(&currentSeq); err != nil {
		return model.SyncPullResponse{}, err
	}

	notes, notesMore, err := s.queryNotesSince(ctx, userID, sinceSeq, limit)
	if err != nil {
		return model.SyncPullResponse{}, err
	}
	items, itemsMore, err := s.queryShoppingSince(ctx, userID, sinceSeq, limit)
	if err != nil {
		return model.SyncPullResponse{}, err
	}

	return model.SyncPullResponse{
		InstanceID:    s.store.InstanceID,
		ServerVersion: config.Version,
		APIVersion:    config.APIVersion,
		CurrentSeq:    currentSeq,
		HasMore:       notesMore || itemsMore,
		Notes:         notes,
		ShoppingItems: items,
	}, nil
}

// Push applies an atomic, idempotent batch of client mutations with zero silent data loss.
func (s *Service) Push(ctx context.Context, userID string, req model.SyncPushRequest) (model.SyncPushResponse, error) {
	if len(req.Mutations) > 250 {
		return model.SyncPushResponse{}, fmt.Errorf("%w: maximum 250 mutations per push", ErrInvalidInput)
	}
	device := strings.TrimSpace(req.ClientDevice)
	if device == "" {
		device = "Device"
	}
	device = truncateRunes(device, 40)

	var (
		results    = make([]model.SyncMutationResult, 0, len(req.Mutations))
		currentSeq int64
	)

	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		for _, mut := range req.Mutations {
			res, err := s.applyMutationTx(ctx, tx, userID, device, mut)
			if err != nil {
				return err
			}
			results = append(results, res)
		}
		return tx.QueryRowContext(ctx, `SELECT sync_seq FROM users WHERE id = ?`, userID).Scan(&currentSeq)
	})
	if err != nil {
		return model.SyncPushResponse{}, err
	}

	return model.SyncPushResponse{
		InstanceID: s.store.InstanceID,
		CurrentSeq: currentSeq,
		Results:    results,
	}, nil
}

// ListNotes returns all non-purged notes for standard CRUD or export.
func (s *Service) ListNotes(ctx context.Context, userID string, includeTrashed bool) ([]model.Note, error) {
	query := `
		SELECT id, title, body, color, is_pinned, is_read_only, version, server_seq,
		       conflict_of_note_id, created_at, updated_at, trashed_at, purged_at
		FROM notes
		WHERE user_id = ? AND purged_at IS NULL
	`
	if !includeTrashed {
		query += ` AND trashed_at IS NULL`
	}
	query += ` ORDER BY is_pinned DESC, updated_at DESC`

	rows, err := s.store.DB.QueryContext(ctx, query, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanNotes(rows)
}

// GetNote fetches a single non-purged note owned by userID.
func (s *Service) GetNote(ctx context.Context, userID, noteID string) (model.Note, error) {
	var n model.Note
	err := s.scanNoteRow(s.store.DB.QueryRowContext(ctx, `
		SELECT id, title, body, color, is_pinned, is_read_only, version, server_seq,
		       conflict_of_note_id, created_at, updated_at, trashed_at, purged_at
		FROM notes
		WHERE id = ? AND user_id = ? AND purged_at IS NULL
	`, noteID, userID), &n)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Note{}, ErrNotFound
	}
	return n, err
}

// CreateNote creates a note directly via CRUD API.
func (s *Service) CreateNote(ctx context.Context, userID, noteID, title, body, color string, isPinned, isReadOnly bool) (model.Note, error) {
	if noteID == "" {
		noteID = id.NewUUIDv7()
	} else if !id.IsValidUUID(noteID) {
		return model.Note{}, fmt.Errorf("%w: invalid note id", ErrInvalidInput)
	}
	color = normalizeNoteColor(color)
	if err := s.validateNoteContent(title, body); err != nil {
		return model.Note{}, err
	}
	if strings.TrimSpace(title) == "" && strings.TrimSpace(body) == "" {
		return model.Note{}, fmt.Errorf("%w: empty note cannot be saved", ErrInvalidInput)
	}

	now := time.Now().UnixMilli()
	var created model.Note
	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		_, err = tx.ExecContext(ctx, `
			INSERT INTO notes (id, user_id, title, body, color, is_pinned, is_read_only, version, server_seq, created_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
		`, noteID, userID, title, body, color, boolToInt(isPinned), boolToInt(isReadOnly), seq, now, now)
		if err != nil {
			return err
		}
		created = model.Note{
			ID:         noteID,
			Title:      title,
			Body:       body,
			Color:      color,
			IsPinned:   isPinned,
			IsReadOnly: isReadOnly,
			Version:    1,
			ServerSeq:  seq,
			CreatedAt:  now,
			UpdatedAt:  now,
		}
		return nil
	})
	return created, err
}

// UpdateNote updates a note via CRUD API, strictly enforcing read-only protection.
func (s *Service) UpdateNote(ctx context.Context, userID, noteID string, title, body, color *string, isPinned *bool) (model.Note, error) {
	now := time.Now().UnixMilli()
	var updated model.Note
	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		cur, err := s.loadNoteTx(ctx, tx, userID, noteID)
		if err != nil {
			return err
		}
		if cur.PurgedAt != nil {
			return ErrNotFound
		}

		newTitle := cur.Title
		newBody := cur.Body
		contentChanged := false
		if title != nil && *title != cur.Title {
			newTitle = *title
			contentChanged = true
		}
		if body != nil && *body != cur.Body {
			newBody = *body
			contentChanged = true
		}
		if contentChanged && cur.IsReadOnly {
			return ErrNoteReadOnly
		}
		if err := s.validateNoteContent(newTitle, newBody); err != nil {
			return err
		}

		newColor := cur.Color
		if color != nil {
			newColor = normalizeNoteColor(*color)
		}
		newPinned := cur.IsPinned
		if isPinned != nil {
			newPinned = *isPinned
		}

		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		newVersion := cur.Version + 1
		updatedAt := cur.UpdatedAt
		if contentChanged {
			updatedAt = now
		}

		_, err = tx.ExecContext(ctx, `
			UPDATE notes
			SET title = ?, body = ?, color = ?, is_pinned = ?, version = ?, server_seq = ?, updated_at = ?
			WHERE id = ? AND user_id = ?
		`, newTitle, newBody, newColor, boolToInt(newPinned), newVersion, seq, updatedAt, noteID, userID)
		if err != nil {
			return err
		}
		updated = cur
		updated.Title = newTitle
		updated.Body = newBody
		updated.Color = newColor
		updated.IsPinned = newPinned
		updated.Version = newVersion
		updated.ServerSeq = seq
		updated.UpdatedAt = updatedAt
		return nil
	})
	return updated, err
}

// SetNoteReadOnly explicitly locks or unlocks a note.
func (s *Service) SetNoteReadOnly(ctx context.Context, userID, noteID string, isReadOnly bool) (model.Note, error) {
	now := time.Now().UnixMilli()
	var updated model.Note
	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		cur, err := s.loadNoteTx(ctx, tx, userID, noteID)
		if err != nil {
			return err
		}
		if cur.PurgedAt != nil {
			return ErrNotFound
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		newVersion := cur.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET is_read_only = ?, version = ?, server_seq = ?
			WHERE id = ? AND user_id = ?
		`, boolToInt(isReadOnly), newVersion, seq, noteID, userID)
		if err != nil {
			return err
		}
		updated = cur
		updated.IsReadOnly = isReadOnly
		updated.Version = newVersion
		updated.ServerSeq = seq
		return nil
	})
	return updated, err
}

// DeleteNote soft-trashes a note (or purges if permanent=true).
func (s *Service) DeleteNote(ctx context.Context, userID, noteID string, permanent bool) error {
	now := time.Now().UnixMilli()
	return s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		cur, err := s.loadNoteTx(ctx, tx, userID, noteID)
		if err != nil {
			return err
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		if permanent {
			_, err = tx.ExecContext(ctx, `
				UPDATE notes
				SET title = '', body = '', trashed_at = COALESCE(trashed_at, ?), purged_at = ?,
				    version = version + 1, server_seq = ?, updated_at = ?
				WHERE id = ? AND user_id = ?
			`, now, now, seq, now, cur.ID, userID)
			return err
		}
		_, err = tx.ExecContext(ctx, `
			UPDATE notes
			SET trashed_at = ?, is_pinned = 0, version = version + 1, server_seq = ?
			WHERE id = ? AND user_id = ?
		`, now, seq, cur.ID, userID)
		return err
	})
}

// ListShoppingItems returns all non-deleted shopping items ordered for display.
func (s *Service) ListShoppingItems(ctx context.Context, userID string) ([]model.ShoppingItem, error) {
	rows, err := s.store.DB.QueryContext(ctx, `
		SELECT id, text, is_checked, sort_order, version, server_seq, created_at, updated_at, deleted_at
		FROM shopping_items
		WHERE user_id = ? AND deleted_at IS NULL
		ORDER BY is_checked ASC, sort_order ASC, id ASC
	`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	return scanShoppingItems(rows)
}

// UpsertShoppingItem creates or updates a shopping item via CRUD API.
func (s *Service) UpsertShoppingItem(ctx context.Context, userID, itemID, text string, isChecked bool, sortOrder string) (model.ShoppingItem, error) {
	text = strings.TrimSpace(text)
	if text == "" || len(text) > s.cfg.MaxItemTextLen {
		return model.ShoppingItem{}, fmt.Errorf("%w: shopping item text must be 1-%d chars", ErrInvalidInput, s.cfg.MaxItemTextLen)
	}
	if itemID == "" {
		itemID = id.NewUUIDv7()
	} else if !id.IsValidUUID(itemID) {
		return model.ShoppingItem{}, fmt.Errorf("%w: invalid item id", ErrInvalidInput)
	}
	sortOrder = strings.TrimSpace(sortOrder)
	if sortOrder == "" {
		sortOrder = fmt.Sprintf("m%013d", time.Now().UnixMilli())
	} else if !isValidSortOrder(sortOrder) {
		return model.ShoppingItem{}, fmt.Errorf("%w: invalid sort_order", ErrInvalidInput)
	}

	now := time.Now().UnixMilli()
	var result model.ShoppingItem
	err := s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		existing, err := s.loadShoppingTx(ctx, tx, userID, itemID)
		if errors.Is(err, ErrNotFound) {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO shopping_items (id, user_id, text, is_checked, sort_order, version, server_seq, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)
			`, itemID, userID, text, boolToInt(isChecked), sortOrder, seq, now, now)
			if err != nil {
				return err
			}
			result = model.ShoppingItem{
				ID:        itemID,
				Text:      text,
				IsChecked: isChecked,
				SortOrder: sortOrder,
				Version:   1,
				ServerSeq: seq,
				CreatedAt: now,
				UpdatedAt: now,
			}
			return nil
		}
		if err != nil {
			return err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE shopping_items
			SET text = ?, is_checked = ?, sort_order = ?, version = ?, server_seq = ?, updated_at = ?, deleted_at = NULL
			WHERE id = ? AND user_id = ?
		`, text, boolToInt(isChecked), sortOrder, newVer, seq, now, itemID, userID)
		if err != nil {
			return err
		}
		result = existing
		result.Text = text
		result.IsChecked = isChecked
		result.SortOrder = sortOrder
		result.Version = newVer
		result.ServerSeq = seq
		result.UpdatedAt = now
		result.DeletedAt = nil
		return nil
	})
	return result, err
}

// DeleteShoppingItem soft-deletes a shopping item tombstone for sync propagation.
func (s *Service) DeleteShoppingItem(ctx context.Context, userID, itemID string) error {
	now := time.Now().UnixMilli()
	return s.store.WithWriteTx(ctx, func(tx *sql.Tx) error {
		if _, err := s.loadShoppingTx(ctx, tx, userID, itemID); err != nil {
			return err
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return err
		}
		_, err = tx.ExecContext(ctx, `
			UPDATE shopping_items SET deleted_at = ?, version = version + 1, server_seq = ?, updated_at = ?
			WHERE id = ? AND user_id = ?
		`, now, seq, now, itemID, userID)
		return err
	})
}

type noteUpsertPayload struct {
	Title      string `json:"title"`
	Body       string `json:"body"`
	Color      string `json:"color"`
	IsPinned   bool   `json:"is_pinned"`
	IsReadOnly bool   `json:"is_read_only"`
	CreatedAt  int64  `json:"created_at"`
	UpdatedAt  int64  `json:"updated_at"`
}

type noteFlagPayload struct {
	Value bool `json:"value"`
}

type noteColorPayload struct {
	Color string `json:"color"`
}

type shoppingUpsertPayload struct {
	Text      string `json:"text"`
	IsChecked bool   `json:"is_checked"`
	SortOrder string `json:"sort_order"`
	CreatedAt int64  `json:"created_at"`
	UpdatedAt int64  `json:"updated_at"`
}

func (s *Service) applyMutationTx(ctx context.Context, tx *sql.Tx, userID, device string, mut model.SyncMutation) (model.SyncMutationResult, error) {
	if !id.IsValidUUID(mut.MutationID) || !id.IsValidUUID(mut.EntityID) {
		return model.SyncMutationResult{
			MutationID:   mut.MutationID,
			Entity:       mut.Entity,
			EntityID:     mut.EntityID,
			Status:       "rejected",
			ErrorCode:    "INVALID_ID",
			ErrorMessage: "mutation_id and entity_id must be valid UUIDs",
		}, nil
	}

	// Idempotency check: return cached result if already processed.
	var cachedJSON string
	err := tx.QueryRowContext(ctx, `
		SELECT response_json FROM processed_mutations WHERE mutation_id = ? AND user_id = ?
	`, mut.MutationID, userID).Scan(&cachedJSON)
	if err == nil {
		var cached model.SyncMutationResult
		if uerr := json.Unmarshal([]byte(cachedJSON), &cached); uerr == nil {
			cached.Status = "duplicate"
			return cached, nil
		}
	} else if !errors.Is(err, sql.ErrNoRows) {
		return model.SyncMutationResult{}, err
	}

	var res model.SyncMutationResult
	switch mut.Entity {
	case "note":
		res, err = s.applyNoteMutationTx(ctx, tx, userID, device, mut)
	case "shopping_item":
		res, err = s.applyShoppingMutationTx(ctx, tx, userID, mut)
	default:
		res = model.SyncMutationResult{
			MutationID:   mut.MutationID,
			Entity:       mut.Entity,
			EntityID:     mut.EntityID,
			Status:       "rejected",
			ErrorCode:    "UNKNOWN_ENTITY",
			ErrorMessage: "unsupported entity type",
		}
	}
	if err != nil {
		return model.SyncMutationResult{}, err
	}

	// Cache outcome for idempotent retries.
	resBytes, err := json.Marshal(res)
	if err != nil {
		return model.SyncMutationResult{}, err
	}
	if _, err := tx.ExecContext(ctx, `
		INSERT OR REPLACE INTO processed_mutations (mutation_id, user_id, response_json, created_at)
		VALUES (?, ?, ?, ?)
	`, mut.MutationID, userID, string(resBytes), time.Now().UnixMilli()); err != nil {
		return model.SyncMutationResult{}, err
	}

	return res, nil
}

func (s *Service) applyNoteMutationTx(ctx context.Context, tx *sql.Tx, userID, device string, mut model.SyncMutation) (model.SyncMutationResult, error) {
	now := time.Now().UnixMilli()
	existing, loadErr := s.loadNoteTx(ctx, tx, userID, mut.EntityID)
	exists := loadErr == nil
	if loadErr != nil && !errors.Is(loadErr, ErrNotFound) {
		return model.SyncMutationResult{}, loadErr
	}

	switch mut.Op {
	case "upsert":
		var p noteUpsertPayload
		if err := json.Unmarshal(mut.Payload, &p); err != nil {
			return rejectedResult(mut, "INVALID_PAYLOAD", "malformed note payload"), nil
		}
		if err := s.validateNoteContent(p.Title, p.Body); err != nil {
			return rejectedResult(mut, "VALIDATION_ERROR", err.Error()), nil
		}
		if strings.TrimSpace(p.Title) == "" && strings.TrimSpace(p.Body) == "" {
			return rejectedResult(mut, "EMPTY_NOTE", "empty note cannot be saved"), nil
		}
		createdAt := p.CreatedAt
		if createdAt <= 0 {
			createdAt = now
		}
		updatedAt := p.UpdatedAt
		if updatedAt <= 0 {
			updatedAt = now
		}

		color := normalizeNoteColor(p.Color)
		if !exists {
			seq, err := nextUserSeqTx(ctx, tx, userID, now)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			_, err = tx.ExecContext(ctx, `
				INSERT INTO notes (id, user_id, title, body, color, is_pinned, is_read_only, version, server_seq, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?)
			`, mut.EntityID, userID, p.Title, p.Body, color, boolToInt(p.IsPinned), boolToInt(p.IsReadOnly), seq, createdAt, updatedAt)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			n := model.Note{
				ID:         mut.EntityID,
				Title:      p.Title,
				Body:       p.Body,
				Color:      color,
				IsPinned:   p.IsPinned,
				IsReadOnly: p.IsReadOnly,
				Version:    1,
				ServerSeq:  seq,
				CreatedAt:  createdAt,
				UpdatedAt:  updatedAt,
			}
			return model.SyncMutationResult{
				MutationID: mut.MutationID,
				Entity:     "note",
				EntityID:   n.ID,
				Status:     "applied",
				Note:       &n,
			}, nil
		}

		// If identical content, apply cleanly without duplicating.
		contentDiffers := existing.Title != p.Title || existing.Body != p.Body

		// Conflict Scenario A: Note was locked (read-only) on another device while edited offline here.
		// Never break the lock AND never lose the offline edits -> preserve locked original, save conflict copy!
		if existing.IsReadOnly && contentDiffers {
			copyNote, err := s.createConflictCopyTx(ctx, tx, userID, existing, p.Title, p.Body, device, now)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			return model.SyncMutationResult{
				MutationID:   mut.MutationID,
				Entity:       "note",
				EntityID:     existing.ID,
				Status:       "conflict_copy_created",
				Note:         &existing,
				ConflictCopy: &copyNote,
			}, nil
		}

		// Conflict Scenario B: Note was purged or edited concurrently on another device (base_version mismatch).
		if existing.PurgedAt != nil {
			// Restore as a new conflict copy so active offline edits are never silently discarded.
			copyNote, err := s.createConflictCopyTx(ctx, tx, userID, existing, p.Title, p.Body, device, now)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			return model.SyncMutationResult{
				MutationID:   mut.MutationID,
				Entity:       "note",
				EntityID:     existing.ID,
				Status:       "conflict_copy_created",
				Note:         &existing,
				ConflictCopy: &copyNote,
			}, nil
		}

		if mut.BaseVersion > 0 && mut.BaseVersion < existing.Version && contentDiffers {
			copyNote, err := s.createConflictCopyTx(ctx, tx, userID, existing, p.Title, p.Body, device, now)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			return model.SyncMutationResult{
				MutationID:   mut.MutationID,
				Entity:       "note",
				EntityID:     existing.ID,
				Status:       "conflict_copy_created",
				Note:         &existing,
				ConflictCopy: &copyNote,
			}, nil
		}

		// Normal update OR Edit-wins-over-Trash (restores trashed_at to NULL when edited offline).
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		finalColor := existing.Color
		if color != "" {
			finalColor = color
		}
		_, err = tx.ExecContext(ctx, `
			UPDATE notes
			SET title = ?, body = ?, color = ?, is_pinned = ?, version = ?, server_seq = ?, updated_at = ?, trashed_at = NULL
			WHERE id = ? AND user_id = ?
		`, p.Title, p.Body, finalColor, boolToInt(p.IsPinned), newVer, seq, updatedAt, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.Title = p.Title
		existing.Body = p.Body
		existing.Color = finalColor
		existing.IsPinned = p.IsPinned
		existing.Version = newVer
		existing.ServerSeq = seq
		existing.UpdatedAt = updatedAt
		existing.TrashedAt = nil

		return model.SyncMutationResult{
			MutationID: mut.MutationID,
			Entity:     "note",
			EntityID:   existing.ID,
			Status:     "applied",
			Note:       &existing,
		}, nil

	case "set_color":
		if !exists || existing.PurgedAt != nil {
			return rejectedResult(mut, "NOT_FOUND", "note not found"), nil
		}
		var p noteColorPayload
		if err := json.Unmarshal(mut.Payload, &p); err != nil {
			return rejectedResult(mut, "INVALID_PAYLOAD", "malformed color payload"), nil
		}
		col := normalizeNoteColor(p.Color)
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET color = ?, version = ?, server_seq = ? WHERE id = ? AND user_id = ?
		`, col, newVer, seq, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.Color = col
		existing.Version = newVer
		existing.ServerSeq = seq
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	case "set_pin":
		if !exists || existing.PurgedAt != nil {
			return rejectedResult(mut, "NOT_FOUND", "note not found"), nil
		}
		var p noteFlagPayload
		if err := json.Unmarshal(mut.Payload, &p); err != nil {
			return rejectedResult(mut, "INVALID_PAYLOAD", "malformed pin payload"), nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET is_pinned = ?, version = ?, server_seq = ? WHERE id = ? AND user_id = ?
		`, boolToInt(p.Value), newVer, seq, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.IsPinned = p.Value
		existing.Version = newVer
		existing.ServerSeq = seq
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	case "set_lock":
		if !exists || existing.PurgedAt != nil {
			return rejectedResult(mut, "NOT_FOUND", "note not found"), nil
		}
		var p noteFlagPayload
		if err := json.Unmarshal(mut.Payload, &p); err != nil {
			return rejectedResult(mut, "INVALID_PAYLOAD", "malformed lock payload"), nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET is_read_only = ?, version = ?, server_seq = ? WHERE id = ? AND user_id = ?
		`, boolToInt(p.Value), newVer, seq, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.IsReadOnly = p.Value
		existing.Version = newVer
		existing.ServerSeq = seq
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	case "trash":
		if !exists || existing.PurgedAt != nil {
			return rejectedResult(mut, "NOT_FOUND", "note not found"), nil
		}
		// Edit-wins-over-Trash: if another device updated the note after client's base_version, do not silently trash it.
		if mut.BaseVersion > 0 && mut.BaseVersion < existing.Version && existing.TrashedAt == nil {
			return model.SyncMutationResult{
				MutationID: mut.MutationID,
				Entity:     "note",
				EntityID:   existing.ID,
				Status:     "applied",
				Note:       &existing,
			}, nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET trashed_at = ?, is_pinned = 0, version = ?, server_seq = ? WHERE id = ? AND user_id = ?
		`, now, newVer, seq, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.TrashedAt = &now
		existing.IsPinned = false
		existing.Version = newVer
		existing.ServerSeq = seq
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	case "restore":
		if !exists || existing.PurgedAt != nil {
			return rejectedResult(mut, "NOT_FOUND", "note not found"), nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes SET trashed_at = NULL, version = ?, server_seq = ? WHERE id = ? AND user_id = ?
		`, newVer, seq, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.TrashedAt = nil
		existing.Version = newVer
		existing.ServerSeq = seq
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	case "purge":
		if !exists {
			return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: mut.EntityID, Status: "applied"}, nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE notes
			SET title = '', body = '', trashed_at = COALESCE(trashed_at, ?), purged_at = ?, is_pinned = 0,
			    version = ?, server_seq = ?, updated_at = ?
			WHERE id = ? AND user_id = ?
		`, now, now, newVer, seq, now, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.Title = ""
		existing.Body = ""
		existing.IsPinned = false
		if existing.TrashedAt == nil {
			existing.TrashedAt = &now
		}
		existing.PurgedAt = &now
		existing.Version = newVer
		existing.ServerSeq = seq
		existing.UpdatedAt = now
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "note", EntityID: existing.ID, Status: "applied", Note: &existing}, nil

	default:
		return rejectedResult(mut, "UNKNOWN_OP", "unsupported note operation"), nil
	}
}

func (s *Service) applyShoppingMutationTx(ctx context.Context, tx *sql.Tx, userID string, mut model.SyncMutation) (model.SyncMutationResult, error) {
	now := time.Now().UnixMilli()
	existing, loadErr := s.loadShoppingTx(ctx, tx, userID, mut.EntityID)
	exists := loadErr == nil
	if loadErr != nil && !errors.Is(loadErr, ErrNotFound) {
		return model.SyncMutationResult{}, loadErr
	}

	switch mut.Op {
	case "upsert":
		var p shoppingUpsertPayload
		if err := json.Unmarshal(mut.Payload, &p); err != nil {
			return rejectedResult(mut, "INVALID_PAYLOAD", "malformed shopping item payload"), nil
		}
		p.Text = strings.TrimSpace(p.Text)
		if p.Text == "" || len(p.Text) > s.cfg.MaxItemTextLen {
			return rejectedResult(mut, "VALIDATION_ERROR", "shopping item text is invalid"), nil
		}
		p.SortOrder = strings.TrimSpace(p.SortOrder)
		if p.SortOrder == "" {
			p.SortOrder = fmt.Sprintf("m%013d", now)
		} else if !isValidSortOrder(p.SortOrder) {
			return rejectedResult(mut, "VALIDATION_ERROR", "invalid sort_order"), nil
		}
		createdAt := p.CreatedAt
		if createdAt <= 0 {
			createdAt = now
		}
		updatedAt := p.UpdatedAt
		if updatedAt <= 0 {
			updatedAt = now
		}

		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		if !exists {
			_, err = tx.ExecContext(ctx, `
				INSERT INTO shopping_items (id, user_id, text, is_checked, sort_order, version, server_seq, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, 1, ?, ?, ?)
			`, mut.EntityID, userID, p.Text, boolToInt(p.IsChecked), p.SortOrder, seq, createdAt, updatedAt)
			if err != nil {
				return model.SyncMutationResult{}, err
			}
			item := model.ShoppingItem{
				ID:        mut.EntityID,
				Text:      p.Text,
				IsChecked: p.IsChecked,
				SortOrder: p.SortOrder,
				Version:   1,
				ServerSeq: seq,
				CreatedAt: createdAt,
				UpdatedAt: updatedAt,
			}
			return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "shopping_item", EntityID: item.ID, Status: "applied", ShoppingItem: &item}, nil
		}

		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE shopping_items
			SET text = ?, is_checked = ?, sort_order = ?, version = ?, server_seq = ?, updated_at = ?, deleted_at = NULL
			WHERE id = ? AND user_id = ?
		`, p.Text, boolToInt(p.IsChecked), p.SortOrder, newVer, seq, updatedAt, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.Text = p.Text
		existing.IsChecked = p.IsChecked
		existing.SortOrder = p.SortOrder
		existing.Version = newVer
		existing.ServerSeq = seq
		existing.UpdatedAt = updatedAt
		existing.DeletedAt = nil
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "shopping_item", EntityID: existing.ID, Status: "applied", ShoppingItem: &existing}, nil

	case "delete":
		if !exists {
			return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "shopping_item", EntityID: mut.EntityID, Status: "applied"}, nil
		}
		seq, err := nextUserSeqTx(ctx, tx, userID, now)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		newVer := existing.Version + 1
		_, err = tx.ExecContext(ctx, `
			UPDATE shopping_items SET deleted_at = ?, version = ?, server_seq = ?, updated_at = ?
			WHERE id = ? AND user_id = ?
		`, now, newVer, seq, now, existing.ID, userID)
		if err != nil {
			return model.SyncMutationResult{}, err
		}
		existing.DeletedAt = &now
		existing.Version = newVer
		existing.ServerSeq = seq
		existing.UpdatedAt = now
		return model.SyncMutationResult{MutationID: mut.MutationID, Entity: "shopping_item", EntityID: existing.ID, Status: "applied", ShoppingItem: &existing}, nil

	default:
		return rejectedResult(mut, "UNKNOWN_OP", "unsupported shopping item operation"), nil
	}
}

func (s *Service) createConflictCopyTx(ctx context.Context, tx *sql.Tx, userID string, original model.Note, title, body, device string, now int64) (model.Note, error) {
	copyID := id.NewUUIDv7()
	seq, err := nextUserSeqTx(ctx, tx, userID, now)
	if err != nil {
		return model.Note{}, err
	}

	conflictTitle := strings.TrimSpace(title)
	if conflictTitle == "" {
		conflictTitle = fmt.Sprintf("Konflikt (%s)", device)
	} else {
		conflictTitle = fmt.Sprintf("%s (konflikt – %s)", conflictTitle, device)
	}
	if utf8.RuneCountInString(conflictTitle) > s.cfg.MaxNoteTitleLen {
		conflictTitle = truncateRunes(conflictTitle, s.cfg.MaxNoteTitleLen)
	}

	refID := original.ID
	_, err = tx.ExecContext(ctx, `
		INSERT INTO notes (id, user_id, title, body, color, is_pinned, is_read_only, version, server_seq, conflict_of_note_id, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, 0, 0, 1, ?, ?, ?, ?)
	`, copyID, userID, conflictTitle, body, original.Color, seq, refID, now, now)
	if err != nil {
		return model.Note{}, err
	}

	return model.Note{
		ID:               copyID,
		Title:            conflictTitle,
		Body:             body,
		Color:            original.Color,
		IsPinned:         false,
		IsReadOnly:       false,
		Version:          1,
		ServerSeq:        seq,
		ConflictOfNoteID: &refID,
		CreatedAt:        now,
		UpdatedAt:        now,
	}, nil
}

func (s *Service) validateNoteContent(title, body string) error {
	if len(title) > s.cfg.MaxNoteTitleLen {
		return fmt.Errorf("%w: title exceeds %d characters", ErrInvalidInput, s.cfg.MaxNoteTitleLen)
	}
	if len(body) > s.cfg.MaxNoteBodyBytes {
		return fmt.Errorf("%w: note body exceeds %d bytes", ErrInvalidInput, s.cfg.MaxNoteBodyBytes)
	}
	return nil
}

func (s *Service) queryNotesSince(ctx context.Context, userID string, sinceSeq int64, limit int) ([]model.Note, bool, error) {
	rows, err := s.store.DB.QueryContext(ctx, `
		SELECT id, title, body, color, is_pinned, is_read_only, version, server_seq,
		       conflict_of_note_id, created_at, updated_at, trashed_at, purged_at
		FROM notes
		WHERE user_id = ? AND server_seq > ?
		ORDER BY server_seq ASC
		LIMIT ?
	`, userID, sinceSeq, limit+1)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()

	notes, err := scanNotes(rows)
	if err != nil {
		return nil, false, err
	}
	hasMore := len(notes) > limit
	if hasMore {
		notes = notes[:limit]
	}
	return notes, hasMore, nil
}

func (s *Service) queryShoppingSince(ctx context.Context, userID string, sinceSeq int64, limit int) ([]model.ShoppingItem, bool, error) {
	rows, err := s.store.DB.QueryContext(ctx, `
		SELECT id, text, is_checked, sort_order, version, server_seq, created_at, updated_at, deleted_at
		FROM shopping_items
		WHERE user_id = ? AND server_seq > ?
		ORDER BY server_seq ASC
		LIMIT ?
	`, userID, sinceSeq, limit+1)
	if err != nil {
		return nil, false, err
	}
	defer rows.Close()

	items, err := scanShoppingItems(rows)
	if err != nil {
		return nil, false, err
	}
	hasMore := len(items) > limit
	if hasMore {
		items = items[:limit]
	}
	return items, hasMore, nil
}

func (s *Service) loadNoteTx(ctx context.Context, tx *sql.Tx, userID, noteID string) (model.Note, error) {
	var n model.Note
	err := s.scanNoteRow(tx.QueryRowContext(ctx, `
		SELECT id, title, body, color, is_pinned, is_read_only, version, server_seq,
		       conflict_of_note_id, created_at, updated_at, trashed_at, purged_at
		FROM notes WHERE id = ? AND user_id = ?
	`, noteID, userID), &n)
	if errors.Is(err, sql.ErrNoRows) {
		return model.Note{}, ErrNotFound
	}
	return n, err
}

func (s *Service) loadShoppingTx(ctx context.Context, tx *sql.Tx, userID, itemID string) (model.ShoppingItem, error) {
	var item model.ShoppingItem
	var checked int
	var deletedAt sql.NullInt64
	err := tx.QueryRowContext(ctx, `
		SELECT id, text, is_checked, sort_order, version, server_seq, created_at, updated_at, deleted_at
		FROM shopping_items WHERE id = ? AND user_id = ?
	`, itemID, userID).Scan(
		&item.ID, &item.Text, &checked, &item.SortOrder, &item.Version, &item.ServerSeq,
		&item.CreatedAt, &item.UpdatedAt, &deletedAt,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return model.ShoppingItem{}, ErrNotFound
	}
	if err != nil {
		return model.ShoppingItem{}, err
	}
	item.IsChecked = checked == 1
	if deletedAt.Valid {
		v := deletedAt.Int64
		item.DeletedAt = &v
	}
	return item, nil
}

func (s *Service) scanNoteRow(row *sql.Row, n *model.Note) error {
	var pinned, readOnly int
	var conflictOf sql.NullString
	var trashedAt, purgedAt sql.NullInt64
	err := row.Scan(
		&n.ID, &n.Title, &n.Body, &n.Color, &pinned, &readOnly, &n.Version, &n.ServerSeq,
		&conflictOf, &n.CreatedAt, &n.UpdatedAt, &trashedAt, &purgedAt,
	)
	if err != nil {
		return err
	}
	n.IsPinned = pinned == 1
	n.IsReadOnly = readOnly == 1
	if conflictOf.Valid {
		v := conflictOf.String
		n.ConflictOfNoteID = &v
	}
	if trashedAt.Valid {
		v := trashedAt.Int64
		n.TrashedAt = &v
	}
	if purgedAt.Valid {
		v := purgedAt.Int64
		n.PurgedAt = &v
	}
	return nil
}

func scanNotes(rows *sql.Rows) ([]model.Note, error) {
	out := make([]model.Note, 0)
	for rows.Next() {
		var n model.Note
		var pinned, readOnly int
		var conflictOf sql.NullString
		var trashedAt, purgedAt sql.NullInt64
		if err := rows.Scan(
			&n.ID, &n.Title, &n.Body, &n.Color, &pinned, &readOnly, &n.Version, &n.ServerSeq,
			&conflictOf, &n.CreatedAt, &n.UpdatedAt, &trashedAt, &purgedAt,
		); err != nil {
			return nil, err
		}
		n.IsPinned = pinned == 1
		n.IsReadOnly = readOnly == 1
		if conflictOf.Valid {
			v := conflictOf.String
			n.ConflictOfNoteID = &v
		}
		if trashedAt.Valid {
			v := trashedAt.Int64
			n.TrashedAt = &v
		}
		if purgedAt.Valid {
			v := purgedAt.Int64
			n.PurgedAt = &v
		}
		out = append(out, n)
	}
	return out, rows.Err()
}

func scanShoppingItems(rows *sql.Rows) ([]model.ShoppingItem, error) {
	out := make([]model.ShoppingItem, 0)
	for rows.Next() {
		var item model.ShoppingItem
		var checked int
		var deletedAt sql.NullInt64
		if err := rows.Scan(
			&item.ID, &item.Text, &checked, &item.SortOrder, &item.Version, &item.ServerSeq,
			&item.CreatedAt, &item.UpdatedAt, &deletedAt,
		); err != nil {
			return nil, err
		}
		item.IsChecked = checked == 1
		if deletedAt.Valid {
			v := deletedAt.Int64
			item.DeletedAt = &v
		}
		out = append(out, item)
	}
	return out, rows.Err()
}

func nextUserSeqTx(ctx context.Context, tx *sql.Tx, userID string, now int64) (int64, error) {
	var seq int64
	err := tx.QueryRowContext(ctx, `
		UPDATE users SET sync_seq = sync_seq + 1, updated_at = ?
		WHERE id = ? RETURNING sync_seq
	`, now, userID).Scan(&seq)
	return seq, err
}

func rejectedResult(mut model.SyncMutation, code, msg string) model.SyncMutationResult {
	return model.SyncMutationResult{
		MutationID:   mut.MutationID,
		Entity:       mut.Entity,
		EntityID:     mut.EntityID,
		Status:       "rejected",
		ErrorCode:    code,
		ErrorMessage: msg,
	}
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func truncateRunes(s string, max int) string {
	if utf8.RuneCountInString(s) <= max {
		return s
	}
	return string([]rune(s)[:max])
}

func isValidSortOrder(s string) bool {
	if len(s) == 0 || len(s) > 128 {
		return false
	}
	for i := 0; i < len(s); i++ {
		if s[i] < 0x21 || s[i] > 0x7E {
			return false
		}
	}
	return true
}

func normalizeNoteColor(raw string) string {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "lavender", "pistachio", "mint", "sky", "rose", "salmon", "peach", "butter", "sand", "neutral":
		return strings.ToLower(strings.TrimSpace(raw))
	default:
		return ""
	}
}
