ALTER TABLE problem_authoring_session
    DROP CONSTRAINT IF EXISTS ck_problem_authoring_session_lifecycle;

ALTER TABLE problem_authoring_session
    ADD CONSTRAINT ck_problem_authoring_session_lifecycle
        CHECK (lifecycle_status IN ('DRAFT', 'FINALIZED', 'CANCELLED', 'EXPIRED'));

ALTER TABLE problem_authoring_session
    DROP CONSTRAINT IF EXISTS ck_problem_authoring_session_finalization;

ALTER TABLE problem_authoring_session
    ADD CONSTRAINT ck_problem_authoring_session_finalization
        CHECK ((lifecycle_status = 'DRAFT'
                    AND finalized_question_id IS NULL
                    AND finalized_at IS NULL)
            OR (lifecycle_status = 'FINALIZED'
                    AND finalized_question_id IS NOT NULL
                    AND finalized_at IS NOT NULL)
            OR (lifecycle_status IN ('CANCELLED', 'EXPIRED')
                    AND finalized_question_id IS NULL
                    AND finalized_at IS NULL));

ALTER TABLE problem_asset_storage_task
    ADD COLUMN IF NOT EXISTS source_deleted_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_problem_asset_storage_task_failed_cleanup
    ON problem_asset_storage_task (updated_at)
    WHERE status = 'FAILED' AND source_deleted_at IS NULL;
