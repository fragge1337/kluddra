package db

import (
	"context"
	"database/sql"
	"embed"
	"fmt"
	"io/fs"
	"log/slog"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"sync"
	"time"

	"github.com/fragge1337/kluddra/server/internal/id"
	_ "modernc.org/sqlite"
)

//go:embed migrations/*.sql
var migrationsFS embed.FS

// Store wraps SQLite with safe concurrency, WAL mode, migrations, and hot backups.
type Store struct {
	DB         *sql.DB
	DataDir    string
	DBPath     string
	InstanceID string
	writeMu    sync.Mutex
}

// Open initializes the SQLite database in WAL mode, runs pending migrations, and loads the instance ID.
func Open(ctx context.Context, dataDir string, logger *slog.Logger) (*Store, error) {
	if err := os.MkdirAll(dataDir, 0o750); err != nil {
		return nil, fmt.Errorf("create data directory %q (check container volume permissions): %w", dataDir, err)
	}
	probeFile := filepath.Join(dataDir, ".write_probe")
	if err := os.WriteFile(probeFile, []byte("ok"), 0o600); err != nil {
		return nil, fmt.Errorf("data directory %q is not writable (check Synology/Docker folder permissions): %w", dataDir, err)
	}
	_ = os.Remove(probeFile)

	dbPath := filepath.Join(dataDir, "kluddra.db")
	dsn := fmt.Sprintf("file:%s?_pragma=journal_mode(WAL)&_pragma=synchronous(NORMAL)&_pragma=foreign_keys(ON)&_pragma=busy_timeout(5000)", filepath.ToSlash(dbPath))

	sqlDB, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, fmt.Errorf("open sqlite: %w", err)
	}

	// Serialize writers in-process to prevent SQLITE_BUSY under concurrent sync pushes.
	sqlDB.SetMaxOpenConns(1)
	sqlDB.SetMaxIdleConns(1)
	sqlDB.SetConnMaxLifetime(0)

	if err := sqlDB.PingContext(ctx); err != nil {
		_ = sqlDB.Close()
		return nil, fmt.Errorf("ping sqlite: %w", err)
	}

	// Enforce critical PRAGMAs explicitly on the active connection.
	pragmas := []string{
		"PRAGMA journal_mode = WAL;",
		"PRAGMA synchronous = NORMAL;",
		"PRAGMA foreign_keys = ON;",
		"PRAGMA busy_timeout = 5000;",
	}
	for _, p := range pragmas {
		if _, err := sqlDB.ExecContext(ctx, p); err != nil {
			_ = sqlDB.Close()
			return nil, fmt.Errorf("apply pragma %q: %w", p, err)
		}
	}

	store := &Store{
		DB:      sqlDB,
		DataDir: dataDir,
		DBPath:  dbPath,
	}

	if err := store.migrate(ctx, logger); err != nil {
		_ = sqlDB.Close()
		return nil, err
	}

	instanceID, err := store.ensureInstanceID(ctx)
	if err != nil {
		_ = sqlDB.Close()
		return nil, err
	}
	store.InstanceID = instanceID

	return store, nil
}

// WithWriteTx executes fn inside a serialized BEGIN IMMEDIATE SQLite transaction.
func (s *Store) WithWriteTx(ctx context.Context, fn func(tx *sql.Tx) error) error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	tx, err := s.DB.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	if err := fn(tx); err != nil {
		_ = tx.Rollback()
		return err
	}
	return tx.Commit()
}

// Backup creates a consistent online snapshot using SQLite VACUUM INTO.
func (s *Store) Backup(ctx context.Context, destDir string) (string, error) {
	if err := os.MkdirAll(destDir, 0o750); err != nil {
		return "", fmt.Errorf("create backup dir: %w", err)
	}
	filename := fmt.Sprintf("kluddra-%s.db", time.Now().UTC().Format("20060102-150405"))
	targetPath := filepath.Join(destDir, filename)

	s.writeMu.Lock()
	defer s.writeMu.Unlock()

	escaped := strings.ReplaceAll(filepath.ToSlash(targetPath), "'", "''")
	if _, err := s.DB.ExecContext(ctx, fmt.Sprintf("VACUUM INTO '%s'", escaped)); err != nil {
		return "", fmt.Errorf("sqlite vacuum into backup: %w", err)
	}
	return targetPath, nil
}

// PruneBackups removes backup files older than retainDays.
func PruneBackups(backupDir string, retainDays int) error {
	if retainDays <= 0 {
		return nil
	}
	entries, err := os.ReadDir(backupDir)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	cutoff := time.Now().Add(-time.Duration(retainDays) * 24 * time.Hour)
	for _, e := range entries {
		if e.IsDir() || !strings.HasPrefix(e.Name(), "kluddra-") || !strings.HasSuffix(e.Name(), ".db") {
			continue
		}
		info, err := e.Info()
		if err != nil {
			continue
		}
		if info.ModTime().Before(cutoff) {
			_ = os.Remove(filepath.Join(backupDir, e.Name()))
		}
	}
	return nil
}

// CleanupExpired purges trashed notes older than trashRetainDays and old idempotency entries.
func (s *Store) CleanupExpired(ctx context.Context, trashRetainDays int) error {
	now := time.Now().UnixMilli()
	trashCutoff := now - int64(trashRetainDays)*24*int64(time.Hour/time.Millisecond)
	mutationCutoff := now - 30*24*int64(time.Hour/time.Millisecond)

	return s.WithWriteTx(ctx, func(tx *sql.Tx) error {
		// Soft-trashed notes past retention become purged tombstones so clients learn about purge.
		rows, err := tx.QueryContext(ctx, `
			SELECT DISTINCT user_id FROM notes
			WHERE trashed_at IS NOT NULL AND purged_at IS NULL AND trashed_at < ?
		`, trashCutoff)
		if err != nil {
			return err
		}
		var userIDs []string
		for rows.Next() {
			var uid string
			if err := rows.Scan(&uid); err != nil {
				_ = rows.Close()
				return err
			}
			userIDs = append(userIDs, uid)
		}
		_ = rows.Close()

		for _, uid := range userIDs {
			var seq int64
			if err := tx.QueryRowContext(ctx, `
				UPDATE users SET sync_seq = sync_seq + 1, updated_at = ?
				WHERE id = ? RETURNING sync_seq
			`, now, uid).Scan(&seq); err != nil {
				return err
			}
			if _, err := tx.ExecContext(ctx, `
				UPDATE notes
				SET title = '', body = '', purged_at = ?, server_seq = ?, version = version + 1, updated_at = ?
				WHERE user_id = ? AND trashed_at IS NOT NULL AND purged_at IS NULL AND trashed_at < ?
			`, now, seq, now, uid, trashCutoff); err != nil {
				return err
			}
		}

		if _, err := tx.ExecContext(ctx, `DELETE FROM processed_mutations WHERE created_at < ?`, mutationCutoff); err != nil {
			return err
		}
		if _, err := tx.ExecContext(ctx, `DELETE FROM sessions WHERE expires_at < ?`, now-7*24*int64(time.Hour/time.Millisecond)); err != nil {
			return err
		}
		return nil
	})
}

// CheckpointAndClose flushes the WAL cleanly during graceful shutdown.
func (s *Store) CheckpointAndClose() error {
	s.writeMu.Lock()
	defer s.writeMu.Unlock()
	_, _ = s.DB.Exec("PRAGMA wal_checkpoint(TRUNCATE);")
	return s.DB.Close()
}

func (s *Store) migrate(ctx context.Context, logger *slog.Logger) error {
	if _, err := s.DB.ExecContext(ctx, `
		CREATE TABLE IF NOT EXISTS schema_migrations (
			version    TEXT PRIMARY KEY,
			applied_at INTEGER NOT NULL
		);
	`); err != nil {
		return fmt.Errorf("create schema_migrations: %w", err)
	}

	entries, err := fs.ReadDir(migrationsFS, "migrations")
	if err != nil {
		return fmt.Errorf("read embedded migrations: %w", err)
	}
	var files []string
	for _, e := range entries {
		if !e.IsDir() && strings.HasSuffix(e.Name(), ".sql") {
			files = append(files, e.Name())
		}
	}
	sort.Strings(files)

	applied := make(map[string]bool)
	rows, err := s.DB.QueryContext(ctx, `SELECT version FROM schema_migrations`)
	if err != nil {
		return err
	}
	for rows.Next() {
		var v string
		if err := rows.Scan(&v); err != nil {
			_ = rows.Close()
			return err
		}
		applied[v] = true
	}
	_ = rows.Close()

	var pending []string
	for _, f := range files {
		if !applied[f] {
			pending = append(pending, f)
		}
	}
	if len(pending) == 0 {
		return nil
	}

	// Create automatic pre-migration safety backup if database already has migrations.
	if len(applied) > 0 {
		backupDir := filepath.Join(s.DataDir, "backups")
		if path, berr := s.Backup(ctx, backupDir); berr == nil && logger != nil {
			logger.Info("created pre-migration backup", "path", path)
		}
	}

	for _, f := range pending {
		sqlBytes, err := migrationsFS.ReadFile("migrations/" + f)
		if err != nil {
			return fmt.Errorf("read migration %s: %w", f, err)
		}
		if err := s.WithWriteTx(ctx, func(tx *sql.Tx) error {
			if _, err := tx.ExecContext(ctx, string(sqlBytes)); err != nil {
				return fmt.Errorf("exec migration %s: %w", f, err)
			}
			_, err := tx.ExecContext(ctx, `INSERT INTO schema_migrations (version, applied_at) VALUES (?, ?)`, f, time.Now().UnixMilli())
			return err
		}); err != nil {
			return err
		}
		if logger != nil {
			logger.Info("applied database migration", "version", f)
		}
	}
	return nil
}

func (s *Store) ensureInstanceID(ctx context.Context) (string, error) {
	var existing string
	err := s.DB.QueryRowContext(ctx, `SELECT value FROM server_meta WHERE key = 'instance_id'`).Scan(&existing)
	if err == nil && existing != "" {
		return existing, nil
	}
	newID := id.NewUUIDv7()
	err = s.WithWriteTx(ctx, func(tx *sql.Tx) error {
		_, e := tx.ExecContext(ctx, `INSERT OR IGNORE INTO server_meta (key, value) VALUES ('instance_id', ?)`, newID)
		return e
	})
	if err != nil {
		return "", err
	}
	if err := s.DB.QueryRowContext(ctx, `SELECT value FROM server_meta WHERE key = 'instance_id'`).Scan(&existing); err != nil {
		return "", err
	}
	return existing, nil
}
