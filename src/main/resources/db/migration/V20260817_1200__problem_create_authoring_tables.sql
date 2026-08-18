CREATE TABLE problem_generation_job (
    id                  BIGSERIAL PRIMARY KEY,
    owner_teacher_id    BIGINT NOT NULL,
    client_request_id   UUID NOT NULL,
    job_type            VARCHAR(40) NOT NULL,
    status              VARCHAR(30) NOT NULL,
    started_at          TIMESTAMP,
    completed_at        TIMESTAMP,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    CONSTRAINT fk_problem_generation_job_owner_teacher
        FOREIGN KEY (owner_teacher_id) REFERENCES member_account(id),
    CONSTRAINT uk_problem_generation_job_owner_request
        UNIQUE (owner_teacher_id, client_request_id),
    CONSTRAINT ck_problem_generation_job_type
        CHECK (job_type IN ('GENERAL_LEARNING', 'COMPREHENSIVE_ASSESSMENT', 'PERSONALIZED')),
    CONSTRAINT ck_problem_generation_job_status
        CHECK (status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'PARTIALLY_FAILED', 'FAILED'))
);

CREATE INDEX idx_problem_generation_job_owner_status
    ON problem_generation_job (owner_teacher_id, status);

CREATE TABLE problem_authoring_session (
    id                      BIGSERIAL PRIMARY KEY,
    owner_teacher_id        BIGINT NOT NULL,
    lifecycle_status        VARCHAR(20) NOT NULL,
    operation_status        VARCHAR(20) NOT NULL,
    interaction_status      VARCHAR(30) NOT NULL,
    current_version_id      BIGINT,
    pending_version_id      BIGINT,
    active_request_id       UUID,
    active_base_version_id  BIGINT,
    pending_instructions    JSONB,
    active_command          JSONB,
    edit_schema_version     INTEGER,
    finalized_question_id   BIGINT,
    last_error_code         VARCHAR(100),
    lock_version            BIGINT NOT NULL DEFAULT 0,
    finalized_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL,
    CONSTRAINT fk_problem_authoring_session_owner_teacher
        FOREIGN KEY (owner_teacher_id) REFERENCES member_account(id),
    CONSTRAINT fk_problem_authoring_session_finalized_question
        FOREIGN KEY (finalized_question_id) REFERENCES problem_question(id),
    CONSTRAINT ck_problem_authoring_session_lifecycle
        CHECK (lifecycle_status IN ('DRAFT', 'FINALIZED')),
    CONSTRAINT ck_problem_authoring_session_operation
        CHECK (operation_status IN ('IDLE', 'GENERATING', 'MODIFYING', 'VERIFYING', 'FAILED')),
    CONSTRAINT ck_problem_authoring_session_interaction
        CHECK (interaction_status IN ('IDLE', 'COLLECTING', 'AWAITING_CONFIRMATION')),
    CONSTRAINT ck_problem_authoring_session_current_pending_different
        CHECK (current_version_id IS NULL OR pending_version_id IS NULL
            OR current_version_id <> pending_version_id),
    CONSTRAINT ck_problem_authoring_session_finalization
        CHECK ((lifecycle_status = 'DRAFT'
                    AND finalized_question_id IS NULL
                    AND finalized_at IS NULL)
            OR (lifecycle_status = 'FINALIZED'
                    AND finalized_question_id IS NOT NULL
                    AND finalized_at IS NOT NULL)),
    CONSTRAINT ck_problem_authoring_session_awaiting_payload
        CHECK (interaction_status <> 'AWAITING_CONFIRMATION'
            OR pending_instructions IS NOT NULL),
    CONSTRAINT ck_problem_authoring_session_operation_interaction
        CHECK (operation_status = 'IDLE' OR interaction_status = 'IDLE')
);

CREATE INDEX idx_problem_authoring_session_owner_lifecycle
    ON problem_authoring_session (owner_teacher_id, lifecycle_status);

CREATE INDEX idx_problem_authoring_session_finalized_question
    ON problem_authoring_session (finalized_question_id);

CREATE TABLE problem_generation_item (
    id                      BIGSERIAL PRIMARY KEY,
    job_id                  BIGINT NOT NULL,
    item_order              INTEGER NOT NULL,
    request_id              UUID NOT NULL,
    session_id              BIGINT NOT NULL,
    generation_purpose      VARCHAR(60) NOT NULL,
    command_schema_version  INTEGER NOT NULL,
    generation_command      JSONB NOT NULL,
    status                  VARCHAR(30) NOT NULL,
    retry_count             SMALLINT NOT NULL DEFAULT 0,
    last_error_code         VARCHAR(100),
    started_at              TIMESTAMP,
    completed_at            TIMESTAMP,
    created_at              TIMESTAMP NOT NULL,
    updated_at              TIMESTAMP NOT NULL,
    CONSTRAINT fk_problem_generation_item_job
        FOREIGN KEY (job_id) REFERENCES problem_generation_job(id),
    CONSTRAINT fk_problem_generation_item_session
        FOREIGN KEY (session_id) REFERENCES problem_authoring_session(id),
    CONSTRAINT uk_problem_generation_item_job_order UNIQUE (job_id, item_order),
    CONSTRAINT uk_problem_generation_item_request UNIQUE (request_id),
    CONSTRAINT uk_problem_generation_item_session UNIQUE (session_id),
    CONSTRAINT ck_problem_generation_item_order CHECK (item_order >= 1),
    CONSTRAINT ck_problem_generation_item_retry CHECK (retry_count BETWEEN 0 AND 2),
    CONSTRAINT ck_problem_generation_item_purpose CHECK (generation_purpose IN (
        'GENERAL_LEARNING_SHORTAGE',
        'COMPREHENSIVE_ASSESSMENT_SHORTAGE',
        'PERSONALIZED_SIMILAR_SHORTAGE',
        'PERSONALIZED_APPLICATION'
    )),
    CONSTRAINT ck_problem_generation_item_status CHECK (status IN (
        'QUEUED', 'GENERATING', 'VERIFYING', 'SUCCEEDED', 'FAILED'
    )),
    CONSTRAINT ck_problem_generation_item_command_json
        CHECK (jsonb_typeof(generation_command) = 'object')
);

CREATE INDEX idx_problem_generation_item_job_status
    ON problem_generation_item (job_id, status);

CREATE TABLE problem_authoring_version (
    id                       BIGSERIAL PRIMARY KEY,
    session_id               BIGINT NOT NULL,
    version_no               INTEGER NOT NULL,
    parent_version_id        BIGINT,
    source_request_id        UUID NOT NULL,
    operation_type           VARCHAR(30) NOT NULL,
    source_question_id       BIGINT,
    snapshot_schema_version  INTEGER NOT NULL,
    snapshot                 JSONB NOT NULL,
    asset_manifest           JSONB NOT NULL DEFAULT '{"plans": [], "artifacts": []}'::jsonb,
    change_summary           TEXT,
    verification_request_id  UUID,
    verification_status      VARCHAR(20) NOT NULL,
    verification_report      JSONB,
    verified_at              TIMESTAMP,
    created_at               TIMESTAMP NOT NULL,
    updated_at               TIMESTAMP NOT NULL,
    CONSTRAINT fk_problem_authoring_version_session
        FOREIGN KEY (session_id) REFERENCES problem_authoring_session(id),
    CONSTRAINT fk_problem_authoring_version_source_question
        FOREIGN KEY (source_question_id) REFERENCES problem_question(id),
    CONSTRAINT uk_problem_authoring_version_session_no UNIQUE (session_id, version_no),
    CONSTRAINT uk_problem_authoring_version_session_request
        UNIQUE (session_id, source_request_id),
    CONSTRAINT uk_problem_authoring_version_verification_request
        UNIQUE (verification_request_id),
    CONSTRAINT uk_problem_authoring_version_id_session UNIQUE (id, session_id),
    CONSTRAINT ck_problem_authoring_version_no CHECK (version_no >= 1),
    CONSTRAINT ck_problem_authoring_version_operation CHECK (operation_type IN (
        'BANK_REUSE', 'AI_GENERATE', 'AI_MODIFY'
    )),
    CONSTRAINT ck_problem_authoring_version_verification CHECK (verification_status IN (
        'NOT_STARTED', 'VERIFYING', 'PASSED', 'FAILED', 'ERROR'
    )),
    CONSTRAINT ck_problem_authoring_version_bank_source
        CHECK (operation_type <> 'BANK_REUSE' OR source_question_id IS NOT NULL),
    CONSTRAINT ck_problem_authoring_version_snapshot_json
        CHECK (jsonb_typeof(snapshot) = 'object'),
    CONSTRAINT ck_problem_authoring_version_asset_manifest_json
        CHECK (jsonb_typeof(asset_manifest) = 'object'),
    CONSTRAINT ck_problem_authoring_version_report_json
        CHECK (verification_report IS NULL OR jsonb_typeof(verification_report) = 'object')
);

CREATE INDEX idx_problem_authoring_version_session_created
    ON problem_authoring_version (session_id, created_at);

ALTER TABLE problem_authoring_version
    ADD CONSTRAINT fk_problem_authoring_version_parent_same_session
    FOREIGN KEY (parent_version_id, session_id)
    REFERENCES problem_authoring_version (id, session_id);

ALTER TABLE problem_authoring_session
    ADD CONSTRAINT fk_problem_authoring_session_current_same_session
    FOREIGN KEY (current_version_id, id)
    REFERENCES problem_authoring_version (id, session_id),
    ADD CONSTRAINT fk_problem_authoring_session_pending_same_session
    FOREIGN KEY (pending_version_id, id)
    REFERENCES problem_authoring_version (id, session_id),
    ADD CONSTRAINT fk_problem_authoring_session_base_same_session
    FOREIGN KEY (active_base_version_id, id)
    REFERENCES problem_authoring_version (id, session_id);
