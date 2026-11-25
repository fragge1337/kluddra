-- Security hardening migration: scope idempotency keys per user to prevent cross-user collisions.

CREATE TABLE IF NOT EXISTS processed_mutations_v2 (
    user_id       TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    mutation_id   TEXT NOT NULL,
    response_json TEXT NOT NULL,
    created_at    INTEGER NOT NULL,
    PRIMARY KEY (user_id, mutation_id)
);

INSERT OR IGNORE INTO processed_mutations_v2 (user_id, mutation_id, response_json, created_at)
SELECT user_id, mutation_id, response_json, created_at FROM processed_mutations;

DROP TABLE processed_mutations;
ALTER TABLE processed_mutations_v2 RENAME TO processed_mutations;
CREATE INDEX IF NOT EXISTS idx_mutations_created ON processed_mutations(created_at);
