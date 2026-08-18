ALTER TABLE problem_asset
    ADD COLUMN IF NOT EXISTS storage_status VARCHAR(20) NOT NULL DEFAULT 'READY';

CREATE TABLE IF NOT EXISTS problem_asset_storage_task
(
    id                 BIGSERIAL PRIMARY KEY,
    problem_asset_id   BIGINT NOT NULL UNIQUE,
    source_local_path  VARCHAR(500) NOT NULL,
    target_storage_key VARCHAR(255) NOT NULL UNIQUE,
    status             VARCHAR(20) NOT NULL,
    attempt_count      INTEGER NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMP,
    last_error_code    VARCHAR(100),
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_problem_asset_storage_task_asset
        FOREIGN KEY (problem_asset_id) REFERENCES problem_asset (id),
    CONSTRAINT ck_problem_asset_storage_task_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'READY', 'FAILED'))
);
