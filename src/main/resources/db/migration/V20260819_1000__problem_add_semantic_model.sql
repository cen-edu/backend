ALTER TABLE problem_authoring_version
    ADD COLUMN semantic_model_schema_version SMALLINT,
    ADD COLUMN semantic_model JSONB,
    ADD COLUMN semantic_model_hash VARCHAR(64),
    ADD CONSTRAINT ck_problem_authoring_version_semantic_json
        CHECK (semantic_model IS NULL OR jsonb_typeof(semantic_model) = 'object'),
    ADD CONSTRAINT ck_problem_authoring_version_semantic_tuple
        CHECK ((semantic_model_schema_version IS NULL AND semantic_model IS NULL AND semantic_model_hash IS NULL)
            OR (semantic_model_schema_version = 1 AND semantic_model IS NOT NULL
                AND semantic_model_hash ~ '^[0-9a-f]{64}$'));

ALTER TABLE problem_question
    ADD COLUMN semantic_model_schema_version SMALLINT,
    ADD COLUMN semantic_model JSONB,
    ADD COLUMN semantic_model_hash VARCHAR(64),
    ADD COLUMN semantic_model_status VARCHAR(20) NOT NULL DEFAULT 'ABSENT',
    ADD CONSTRAINT ck_problem_question_semantic_status
        CHECK (semantic_model_status IN ('ABSENT', 'READY', 'UNSUPPORTED', 'FAILED')),
    ADD CONSTRAINT ck_problem_question_semantic_json
        CHECK (semantic_model IS NULL OR jsonb_typeof(semantic_model) = 'object'),
    ADD CONSTRAINT ck_problem_question_semantic_tuple
        CHECK ((semantic_model_status = 'READY' AND semantic_model_schema_version = 1
                AND semantic_model IS NOT NULL AND semantic_model_hash ~ '^[0-9a-f]{64}$')
            OR (semantic_model_status <> 'READY' AND semantic_model_schema_version IS NULL
                AND semantic_model IS NULL AND semantic_model_hash IS NULL));

ALTER TABLE problem_asset
    ADD COLUMN render_spec_schema_version SMALLINT,
    ADD COLUMN render_spec JSONB,
    ADD COLUMN render_spec_hash VARCHAR(64),
    ADD COLUMN renderer_version VARCHAR(30),
    ADD CONSTRAINT ck_problem_asset_render_spec_json
        CHECK (render_spec IS NULL OR jsonb_typeof(render_spec) = 'object'),
    ADD CONSTRAINT ck_problem_asset_render_spec_tuple
        CHECK ((render_spec_schema_version IS NULL AND render_spec IS NULL
                AND render_spec_hash IS NULL AND renderer_version IS NULL)
            OR (render_spec_schema_version = 1 AND render_spec IS NOT NULL
                AND render_spec_hash ~ '^[0-9a-f]{64}$' AND renderer_version IS NOT NULL));
