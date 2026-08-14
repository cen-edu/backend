from __future__ import annotations

import argparse
import json
from pathlib import Path


DOLLAR_TAG = "$reconciled$"
DATA_FILES = {
    "_raw_question": "question.jsonl",
    "_raw_step": "question_step.jsonl",
    "_raw_answer_unit": "question_answer_unit.jsonl",
    "_raw_choice": "question_choice.jsonl",
    "_raw_asset": "question_asset.jsonl",
}


def _sanitize_pg_dump(path: Path) -> str:
    lines = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.startswith("\\restrict ") or line.startswith("\\unrestrict "):
            continue
        if line == "CREATE SCHEMA public;":
            continue
        if line == "SELECT pg_catalog.set_config('search_path', '', false);":
            continue
        lines.append(line)
    return "\n".join(lines).strip() + "\n"


def _raw_json_table_sql(table_name: str, path: Path) -> tuple[str, int]:
    rows = [line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    for index, line in enumerate(rows, start=1):
        if DOLLAR_TAG in line:
            raise ValueError(f"reserved dollar tag found in {path}:{index}")
        try:
            value = json.loads(line)
        except json.JSONDecodeError as error:
            raise ValueError(f"invalid JSON in {path}:{index}: {error}") from error
        if not isinstance(value, dict):
            raise ValueError(f"JSON object required in {path}:{index}")
    values = ",\n".join(f"({DOLLAR_TAG}{line}{DOLLAR_TAG}::jsonb)" for line in rows)
    sql = (
        f"CREATE TEMP TABLE {table_name} (line jsonb NOT NULL) ON COMMIT DROP;\n\n"
        f"INSERT INTO {table_name} (line) VALUES\n{values};\n"
    )
    return sql, len(rows)


def _problem_load_sql(counts: dict[str, int]) -> str:
    return f"""
-- 문제 본체는 source_ref와 교육과정 external_key로 연결한다.
INSERT INTO public.problem_question (
    source_type, source_ref, source_dataset_code, derived_from_question_id,
    sub_unit_id, topic_code, difficulty, question_type, presentation,
    content_blocks, prompt_text, explanation, learning_guide, hint_text,
    verification_status, verification_attempts, deleted_at
)
SELECT
    line->>'source_type',
    line->>'source_ref',
    line->>'source_dataset_code',
    NULL,
    unit.id,
    NULLIF(line->>'topic_code', ''),
    (line->>'difficulty')::smallint,
    line->>'question_type',
    line->>'presentation',
    line->'content_blocks',
    line->>'prompt_text',
    line->>'explanation',
    line->'learning_guide',
    line->>'hint_text',
    line->>'verification_status',
    COALESCE((line->>'verification_attempts')::smallint, 0),
    (line->>'deleted_at')::timestamptz
FROM _raw_question raw
JOIN public.curriculum_unit unit
  ON unit.external_key = raw.line->>'sub_unit_external_key';

-- 파생 문항의 부모도 숫자 PK가 아니라 source_ref로 다시 해석한다.
UPDATE public.problem_question child
SET derived_from_question_id = parent.id
FROM _raw_question raw
JOIN public.problem_question parent
  ON parent.source_ref = raw.line->>'derived_from_source_ref'
WHERE child.source_ref = raw.line->>'source_ref'
  AND raw.line ? 'derived_from_source_ref';

INSERT INTO public.problem_step (question_id, display_order, label, segments)
SELECT
    question.id,
    (raw.line->>'display_order')::smallint,
    raw.line->>'label',
    raw.line->'segments'
FROM _raw_step raw
JOIN public.problem_question question
  ON question.source_ref = raw.line->>'source_ref';

INSERT INTO public.problem_answer_unit (
    question_id, step_id, unit_key, display_order, label, answer_raw,
    answer_normalized, compare_method, diagnostic_type, display_unit
)
SELECT
    question.id,
    step.id,
    raw.line->>'unit_key',
    (raw.line->>'display_order')::integer,
    raw.line->>'label',
    raw.line->>'answer_raw',
    raw.line->>'answer_normalized',
    raw.line->>'compare_method',
    raw.line->>'diagnostic_type',
    raw.line->>'display_unit'
FROM _raw_answer_unit raw
JOIN public.problem_question question
  ON question.source_ref = raw.line->>'source_ref'
LEFT JOIN public.problem_step step
  ON step.question_id = question.id
 AND step.display_order = (raw.line->>'step_display_order')::smallint;

INSERT INTO public.problem_choice (question_id, display_order, content)
SELECT
    question.id,
    (raw.line->>'display_order')::smallint,
    raw.line->>'content'
FROM _raw_choice raw
JOIN public.problem_question question
  ON question.source_ref = raw.line->>'source_ref';

INSERT INTO public.problem_asset (
    question_id, asset_key, role, display_order, storage_key,
    width_px, height_px, alt_text
)
SELECT
    question.id,
    raw.line->>'asset_key',
    raw.line->>'role',
    (raw.line->>'display_order')::smallint,
    raw.line->>'storage_key',
    (raw.line->>'width_px')::integer,
    (raw.line->>'height_px')::integer,
    raw.line->>'alt_text'
FROM _raw_asset raw
JOIN public.problem_question question
  ON question.source_ref = raw.line->>'source_ref';

DO $verify$
DECLARE
    actual_count bigint;
BEGIN
    SELECT count(*) INTO actual_count FROM public.problem_question;
    IF actual_count <> {counts['_raw_question']} THEN
        RAISE EXCEPTION 'problem_question count mismatch: expected {counts['_raw_question']}, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count FROM public.problem_step;
    IF actual_count <> {counts['_raw_step']} THEN
        RAISE EXCEPTION 'problem_step count mismatch: expected {counts['_raw_step']}, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count FROM public.problem_answer_unit;
    IF actual_count <> {counts['_raw_answer_unit']} THEN
        RAISE EXCEPTION 'problem_answer_unit count mismatch: expected {counts['_raw_answer_unit']}, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count FROM public.problem_choice;
    IF actual_count <> {counts['_raw_choice']} THEN
        RAISE EXCEPTION 'problem_choice count mismatch: expected {counts['_raw_choice']}, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count FROM public.problem_asset;
    IF actual_count <> {counts['_raw_asset']} THEN
        RAISE EXCEPTION 'problem_asset count mismatch: expected {counts['_raw_asset']}, got %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM public.problem_question question
    CROSS JOIN LATERAL jsonb_array_elements(question.content_blocks) block
    LEFT JOIN public.problem_asset asset
      ON asset.question_id = question.id
     AND asset.asset_key = block->>'assetRef'
    WHERE block ? 'assetRef'
      AND asset.id IS NULL;
    IF actual_count <> 0 THEN
        RAISE EXCEPTION 'content block asset references without asset: %', actual_count;
    END IF;

    SELECT count(*) INTO actual_count
    FROM public.problem_answer_unit answer_unit
    JOIN public.problem_question question ON question.id = answer_unit.question_id
    WHERE question.question_type = 'STEP_FILL'
      AND answer_unit.step_id IS NULL;
    IF actual_count <> 0 THEN
        RAISE EXCEPTION 'STEP_FILL answer units without step: %', actual_count;
    END IF;
END
$verify$;
""".strip() + "\n"


def generate(schema_sql: Path, reference_sql: Path, dataset_dir: Path, output: Path) -> None:
    sections = [
        "-- 팀 공통 DB baseline. 문제 데이터의 관계는 source_ref 기반으로 해석한다.\n",
        "CREATE EXTENSION IF NOT EXISTS vector;\n",
        _sanitize_pg_dump(schema_sql),
        _sanitize_pg_dump(reference_sql),
    ]
    counts: dict[str, int] = {}
    for table_name, filename in DATA_FILES.items():
        sql, count = _raw_json_table_sql(table_name, dataset_dir / filename)
        sections.append(sql)
        counts[table_name] = count
    sections.append(_problem_load_sql(counts))
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text("\n\n".join(section.rstrip() for section in sections) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate a source_ref-based Flyway baseline")
    parser.add_argument("--schema-sql", type=Path, required=True)
    parser.add_argument("--reference-sql", type=Path, required=True)
    parser.add_argument("--dataset-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    generate(args.schema_sql, args.reference_sql, args.dataset_dir, args.output)


if __name__ == "__main__":
    main()
