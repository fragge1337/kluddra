-- Kluddra Server initial SQLite schema (WAL mode)

CREATE TABLE IF NOT EXISTS server_meta (
    key   TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    id            TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT NOT NULL,
    is_admin      INTEGER NOT NULL DEFAULT 0,
    sync_seq      INTEGER NOT NULL DEFAULT 0,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    id           TEXT PRIMARY KEY,
    user_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   TEXT NOT NULL UNIQUE,
    token_family TEXT NOT NULL,
    device_name  TEXT NOT NULL,
    created_at   INTEGER NOT NULL,
    last_used_at INTEGER NOT NULL,
    expires_at   INTEGER NOT NULL,
    revoked_at   INTEGER
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_family ON sessions(token_family);

CREATE TABLE IF NOT EXISTS notes (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title               TEXT NOT NULL DEFAULT '',
    body                TEXT NOT NULL DEFAULT '',
    is_pinned           INTEGER NOT NULL DEFAULT 0,
    is_read_only        INTEGER NOT NULL DEFAULT 0,
    version             INTEGER NOT NULL DEFAULT 1,
    server_seq          INTEGER NOT NULL,
    conflict_of_note_id TEXT,
    created_at          INTEGER NOT NULL,
    updated_at          INTEGER NOT NULL,
    trashed_at          INTEGER,
    purged_at           INTEGER
);

CREATE INDEX IF NOT EXISTS idx_notes_user_seq ON notes(user_id, server_seq);
CREATE INDEX IF NOT EXISTS idx_notes_user_active ON notes(user_id, trashed_at, purged_at, is_pinned DESC, updated_at DESC);

CREATE TABLE IF NOT EXISTS shopping_items (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text        TEXT NOT NULL,
    is_checked  INTEGER NOT NULL DEFAULT 0,
    sort_order  TEXT NOT NULL,
    version     INTEGER NOT NULL DEFAULT 1,
    server_seq  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL,
    updated_at  INTEGER NOT NULL,
    deleted_at  INTEGER
);

CREATE INDEX IF NOT EXISTS idx_shopping_user_seq ON shopping_items(user_id, server_seq);
CREATE INDEX IF NOT EXISTS idx_shopping_user_active ON shopping_items(user_id, deleted_at, is_checked, sort_order);

CREATE TABLE IF NOT EXISTS processed_mutations (
    mutation_id   TEXT PRIMARY KEY,
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    response_json TEXT NOT NULL,
    created_at    INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mutations_created ON processed_mutations(created_at);
