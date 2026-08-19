CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE curriculum_unit
    ADD COLUMN curriculum_revision VARCHAR(20) NOT NULL DEFAULT '2022_REVISED',
    ADD COLUMN school_level VARCHAR(20) NOT NULL DEFAULT 'MIDDLE',
    ADD COLUMN achievement_standard_id VARCHAR(40);

ALTER TABLE curriculum_unit
    ADD CONSTRAINT ck_curriculum_unit_revision CHECK (curriculum_revision = '2022_REVISED'),
    ADD CONSTRAINT ck_curriculum_unit_school_level CHECK (school_level = 'MIDDLE'),
    ADD CONSTRAINT ck_curriculum_unit_a_stage_grade CHECK (grade = 1);

CREATE TABLE problem_search_index (
    question_id BIGINT PRIMARY KEY REFERENCES problem_question(id),
    curriculum_revision VARCHAR(20) NOT NULL,
    school_level VARCHAR(20) NOT NULL,
    grade SMALLINT NOT NULL,
    semester SMALLINT,
    achievement_standard_id VARCHAR(40),
    sub_unit_id BIGINT NOT NULL,
    question_type VARCHAR(30) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    presentation VARCHAR(20) NOT NULL,
    source_family_key VARCHAR(120) NOT NULL,
    document_text TEXT NOT NULL,
    document_hash CHAR(64) NOT NULL,
    duplicate_cluster_key CHAR(64) NOT NULL,
    concept_keys TEXT[] NOT NULL DEFAULT '{}',
    snapshot JSONB NOT NULL,
    embedding_model VARCHAR(80) NOT NULL,
    embedding_dimensions INTEGER NOT NULL,
    embedding VECTOR(1024),
    index_status VARCHAR(20) NOT NULL,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_problem_search_index_dimensions CHECK (embedding_dimensions = 1024),
    CONSTRAINT ck_problem_search_index_status CHECK (index_status IN ('READY', 'DELETED'))
);
CREATE INDEX idx_problem_search_index_metadata ON problem_search_index
    (curriculum_revision, school_level, grade, sub_unit_id, question_type, difficulty);
CREATE INDEX idx_problem_search_index_embedding_hnsw ON problem_search_index
    USING hnsw (embedding vector_cosine_ops);

CREATE TABLE problem_search_index_task (
    id BIGSERIAL PRIMARY KEY,
    question_id BIGINT NOT NULL UNIQUE REFERENCES problem_question(id),
    idempotency_key VARCHAR(160) NOT NULL UNIQUE,
    command JSONB NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_problem_search_task_status CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'READY', 'SKIPPED', 'FAILED'))
);
CREATE INDEX idx_problem_search_task_due ON problem_search_index_task (next_attempt_at, id)
    WHERE status IN ('PENDING', 'RETRY_WAIT');

CREATE TABLE problem_retrieval_trace (
    id UUID PRIMARY KEY,
    request_id UUID NOT NULL,
    purpose VARCHAR(50) NOT NULL,
    fallback_reason VARCHAR(40),
    job_id BIGINT,
    item_id BIGINT,
    version_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE problem_retrieval_candidate (
    id BIGSERIAL PRIMARY KEY,
    trace_id UUID NOT NULL REFERENCES problem_retrieval_trace(id),
    question_id BIGINT NOT NULL,
    dense_rank INTEGER NOT NULL,
    dense_score DOUBLE PRECISION NOT NULL,
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    selection_rank INTEGER,
    duplicate_cluster_key VARCHAR(120),
    source_family_key VARCHAR(120)
);

CREATE TABLE problem_teacher_decision_event (
    id BIGSERIAL PRIMARY KEY,
    event_key VARCHAR(180) NOT NULL UNIQUE,
    teacher_id BIGINT NOT NULL,
    session_id BIGINT,
    version_id BIGINT,
    decision_type VARCHAR(30) NOT NULL,
    change_natures JSONB,
    target_types JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_problem_teacher_decision_type CHECK (decision_type IN ('APPROVED', 'MODIFICATION_STARTED', 'RESTORED', 'REPLACED', 'DISCARDED'))
);
