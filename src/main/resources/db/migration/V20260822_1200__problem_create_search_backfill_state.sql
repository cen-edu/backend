CREATE TABLE problem_search_backfill_state (
    state_key VARCHAR(64) PRIMARY KEY,
    cursor BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
