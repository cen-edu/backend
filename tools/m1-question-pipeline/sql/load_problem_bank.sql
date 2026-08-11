-- Problem-bank data loader for the ERD tables.
-- Run from the backend directory with psql -v ON_ERROR_STOP=1 -f this_file.
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE _raw_units(line jsonb);
CREATE TEMP TABLE _raw_questions(line jsonb);
CREATE TEMP TABLE _raw_steps_text(line text);
CREATE TEMP TABLE _raw_steps(line jsonb);
CREATE TEMP TABLE _raw_answers(line jsonb);
CREATE TEMP TABLE _raw_choices(line jsonb);
CREATE TEMP TABLE _raw_assets(line jsonb);
CREATE TEMP TABLE _question_map(record_id text PRIMARY KEY, question_id bigint NOT NULL);
CREATE TEMP TABLE _step_map(step_key text PRIMARY KEY, step_id bigint NOT NULL);

\copy _raw_units(line) FROM 'tools/m1-question-pipeline/delivery/canonical/curriculum_units.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')
\copy _raw_questions(line) FROM 'tools/m1-question-pipeline/delivery/db_staging/question.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')
\copy _raw_steps_text(line) FROM 'tools/m1-question-pipeline/delivery/db_staging/question_step.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')
\copy _raw_answers(line) FROM 'tools/m1-question-pipeline/delivery/db_staging/question_answer_unit.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')
\copy _raw_choices(line) FROM 'tools/m1-question-pipeline/delivery/db_staging/question_choice.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')
\copy _raw_assets(line) FROM 'tools/m1-question-pipeline/delivery/db_staging/question_asset.jsonl' WITH (FORMAT csv, DELIMITER E'\x02', QUOTE E'\x03', ESCAPE E'\x04')

INSERT INTO _raw_steps(line)
SELECT replace(line, chr(92)||'u0000', '')::jsonb FROM _raw_steps_text;

INSERT INTO curriculum_unit (external_key, unit_level, name, grade, semester, display_order)
SELECT line->>'curriculum_unit_id', 'SUB_UNIT', line->>'small_unit_name',
       COALESCE(NULLIF(regexp_replace(line->>'grade', '[^0-9]', '', 'g'), '')::smallint, 1),
       NULL, COALESCE((line->>'display_order')::integer, 0)
FROM _raw_units
WHERE line->>'curriculum_unit_id' IS NOT NULL
ON CONFLICT (external_key) DO UPDATE SET name=EXCLUDED.name, display_order=EXCLUDED.display_order;

INSERT INTO problem_question (
  source_type, source_ref, source_dataset_code, sub_unit_id, topic_code,
  difficulty, question_type, presentation, content_blocks, prompt_text,
  explanation, learning_guide, hint_text, verification_status,
  verification_attempts, deleted_at
)
SELECT q.line->>'source_type', q.line->>'source_ref', q.line->>'source_dataset_code',
       u.id, NULLIF(q.line->>'topic_code',''),
       CASE WHEN q.line->>'difficulty' ~ '^[0-9]+$' THEN (q.line->>'difficulty')::smallint ELSE 1 END, q.line->>'question_type',
       q.line->>'presentation', q.line->'content_blocks', q.line->>'prompt_text',
       q.line->>'explanation', NULLIF(q.line->'learning_guide','null'::jsonb),
       q.line->>'hint_text', q.line->>'verification_status', 0, NULL
FROM _raw_questions q
JOIN curriculum_unit u ON u.external_key=q.line->>'sub_unit_id'
WHERE q.line->>'source_ref' IS NOT NULL
ON CONFLICT (source_ref) DO UPDATE SET
  source_type=EXCLUDED.source_type, source_dataset_code=EXCLUDED.source_dataset_code,
  sub_unit_id=EXCLUDED.sub_unit_id, question_type=EXCLUDED.question_type,
  presentation=EXCLUDED.presentation, content_blocks=EXCLUDED.content_blocks,
  prompt_text=EXCLUDED.prompt_text, explanation=EXCLUDED.explanation,
  learning_guide=EXCLUDED.learning_guide, hint_text=EXCLUDED.hint_text;

INSERT INTO _question_map(record_id, question_id)
SELECT q.line->>'question_id', p.id
FROM _raw_questions q JOIN problem_question p ON p.source_ref=q.line->>'source_ref'
ON CONFLICT (record_id) DO UPDATE SET question_id=EXCLUDED.question_id;

INSERT INTO problem_step (question_id, display_order, label, segments)
SELECT m.question_id, (s.line->>'display_order')::smallint,
       s.line->>'title', s.line->'segments'
FROM _raw_steps s JOIN _question_map m ON m.record_id=s.line->>'question_id'
ON CONFLICT (question_id, display_order) DO UPDATE SET
  label=EXCLUDED.label, segments=EXCLUDED.segments;

INSERT INTO _step_map(step_key, step_id)
SELECT (s.line->>'question_id')||'::'||(s.line->>'step_id'), ps.id
FROM _raw_steps s JOIN _question_map qm ON qm.record_id=s.line->>'question_id'
JOIN problem_step ps ON ps.question_id=qm.question_id
                    AND ps.display_order=(s.line->>'display_order')::smallint
ON CONFLICT (step_key) DO UPDATE SET step_id=EXCLUDED.step_id;

INSERT INTO problem_answer_unit (
  question_id, step_id, unit_key, display_order, label, answer_raw,
  answer_normalized, compare_method, diagnostic_type, display_unit
)
SELECT qm.question_id,
       sm.step_id, a.line->>'unit_key', (a.line->>'display_order')::integer,
       a.line->>'label', a.line->>'answer_raw', a.line->>'answer_normalized',
       COALESCE(NULLIF(a.line->>'compare_method',''), 'EXACT'),
       NULLIF(a.line->>'diagnostic_type',''), NULL
FROM _raw_answers a
JOIN _question_map qm ON qm.record_id=a.line->>'question_id'
LEFT JOIN _step_map sm ON sm.step_key=(a.line->>'question_id')||'::'||(a.line->>'step_id')
ON CONFLICT (question_id, unit_key) DO UPDATE SET
  step_id=EXCLUDED.step_id, display_order=EXCLUDED.display_order,
  answer_raw=EXCLUDED.answer_raw, answer_normalized=EXCLUDED.answer_normalized,
  compare_method=EXCLUDED.compare_method, diagnostic_type=EXCLUDED.diagnostic_type;

INSERT INTO problem_choice (question_id, display_order, content)
SELECT qm.question_id, (c.line->>'display_order')::smallint, c.line->>'content'
FROM _raw_choices c JOIN _question_map qm ON qm.record_id=c.line->>'question_id'
ON CONFLICT (question_id, display_order) DO UPDATE SET content=EXCLUDED.content;

INSERT INTO problem_asset (
  question_id, asset_key, role, display_order, storage_key,
  width_px, height_px, alt_text
)
SELECT qm.question_id, a.line->>'asset_key', a.line->>'role',
       (a.line->>'display_order')::smallint, a.line->>'storage_key',
       COALESCE((a.line->>'width_px')::integer, 1),
       COALESCE((a.line->>'height_px')::integer, 1), a.line->>'alt_text'
FROM _raw_assets a JOIN _question_map qm ON qm.record_id=a.line->>'question_id'
ON CONFLICT (question_id, asset_key) DO UPDATE SET
  role=EXCLUDED.role, display_order=EXCLUDED.display_order,
  storage_key=EXCLUDED.storage_key, width_px=EXCLUDED.width_px,
  height_px=EXCLUDED.height_px, alt_text=EXCLUDED.alt_text;

-- 적재 검증. 오류가 있으면 ON_ERROR_STOP으로 중단되며 COMMIT되지 않습니다.
SELECT 'problem_question' AS table_name, count(*) AS row_count FROM problem_question;
SELECT 'problem_step' AS table_name, count(*) AS row_count FROM problem_step;
SELECT 'problem_answer_unit' AS table_name, count(*) AS row_count FROM problem_answer_unit;
SELECT 'problem_choice' AS table_name, count(*) AS row_count FROM problem_choice;
SELECT 'problem_asset' AS table_name, count(*) AS row_count FROM problem_asset;
SELECT 'orphan_question_curriculum' AS check_name, count(*) AS row_count
FROM problem_question q LEFT JOIN curriculum_unit u ON u.id=q.sub_unit_id WHERE u.id IS NULL;
SELECT 'non_step_diagnostic_type' AS check_name, count(*) AS row_count
FROM problem_answer_unit a JOIN problem_question q ON q.id=a.question_id
WHERE q.question_type <> 'STEP_FILL' AND a.diagnostic_type IS NOT NULL;

COMMIT;
