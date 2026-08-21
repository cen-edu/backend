ALTER TABLE problem_search_index
    ADD COLUMN visual_kind VARCHAR(30) NOT NULL DEFAULT 'NONE',
    ADD COLUMN index_schema_version SMALLINT NOT NULL DEFAULT 1;

ALTER TABLE problem_search_index_task
    ADD COLUMN index_schema_version SMALLINT NOT NULL DEFAULT 1;

UPDATE problem_search_index_task
SET command = jsonb_set(
        jsonb_set(command, '{indexSchemaVersion}', '1'::jsonb, true),
        '{visualKind}', '"NONE"'::jsonb, true)
WHERE NOT (command ? 'indexSchemaVersion') OR NOT (command ? 'visualKind');

ALTER TABLE problem_search_index_task
    DROP CONSTRAINT IF EXISTS problem_search_index_task_question_id_key;

ALTER TABLE problem_search_index_task
    ADD CONSTRAINT ck_problem_search_task_schema_version CHECK (index_schema_version >= 1),
    ADD CONSTRAINT uk_problem_search_task_question_schema UNIQUE (question_id, index_schema_version);

ALTER TABLE problem_search_index
    ADD CONSTRAINT ck_problem_search_visual_kind CHECK (visual_kind IN
        ('NONE','UNKNOWN_FIGURE','NUMBER_LINE','COORDINATE_GRAPH','DATA_TABLE','PLANE_GEOMETRY','SOLID_GEOMETRY')),
    ADD CONSTRAINT ck_problem_search_schema_version CHECK (index_schema_version >= 1);

CREATE INDEX idx_problem_search_index_visual_kind
    ON problem_search_index (visual_kind, sub_unit_id, question_type, difficulty);
