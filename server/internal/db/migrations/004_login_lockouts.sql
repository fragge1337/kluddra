-- 004_login_lockouts.sql
-- Persistent progressive login lockout state so brute-force backoff survives container restarts.

CREATE TABLE IF NOT EXISTS login_lockouts (
    scope          TEXT    NOT NULL,
    key            TEXT    NOT NULL,
    failed_count   INTEGER NOT NULL DEFAULT 0,
    locked_until   INTEGER NOT NULL DEFAULT 0,
    last_failed_at INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (scope, key)
);

CREATE INDEX IF NOT EXISTS idx_login_lockouts_last_failed
    ON login_lockouts (last_failed_at);
