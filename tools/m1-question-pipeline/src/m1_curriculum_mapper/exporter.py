from __future__ import annotations

import copy
import json
import re
from collections import Counter
from pathlib import Path
from typing import Iterable

from .answer_slots import compare_answer_unit
from .catalog import curriculum_units


QUESTION_TYPES = {"MULTIPLE_CHOICE", "SHORT_INPUT", "STEP_FILL", "ESSAY"}
SOURCE_DATASETS = ("30", "110", "111")
SOURCE_TYPE_CONTRACT = {
    "30": {"MULTIPLE_CHOICE", "SHORT_INPUT"},
    "110": {"STEP_FILL"},
    "111": {"STEP_FILL", "ESSAY"},
}
DB_TABLES = (
    "question",
    "question_choice",
    "question_step",
    "question_answer_unit",
    "question_rubric_item",
    "question_asset",
)


def _issue(code: str, message: str, path: str) -> dict:
    return {"code": code, "message": message, "path": path}


def _mapping_unit_id(record: dict) -> str:
    mappings = record.get("curriculumMappings") or []
    if len(mappings) != 1 or not isinstance(mappings[0], dict):
        return ""
    return str(mappings[0].get("curriculumUnitId") or "")


def _accepted_value(unit: dict) -> tuple[str, str]:
    accepted = unit.get("accepted") or []
    first = accepted[0] if accepted else {}
    if not isinstance(first, dict):
        first = {"raw": first}
    raw = str(first.get("raw") or "")
    normalized = str(first.get("normalized") or raw)
    return raw, normalized


def _validate_canonical_question(record: dict, known_unit_ids: set[str]) -> list[dict]:
    issues = []
    question_type = str(record.get("questionTypeCode") or "")
    source_ref = str(record.get("sourceRef") or "")
    source_dataset = source_ref.partition(":")[0]
    mappings = record.get("curriculumMappings") or []
    unit_id = _mapping_unit_id(record)

    if question_type not in QUESTION_TYPES:
        issues.append(_issue("QUESTION_TYPE_INVALID", "문항 유형은 네 개의 canonical 유형 중 하나여야 합니다.", "questionTypeCode"))
    if source_dataset == "28":
        issues.append(_issue("SOURCE_28_NOT_QUESTION", "28 데이터는 참고 자료이므로 문항으로 내보낼 수 없습니다.", "sourceRef"))
    elif source_dataset not in SOURCE_TYPE_CONTRACT:
        issues.append(_issue("SOURCE_DATASET_INVALID", "문항 sourceRef는 30, 110, 111 원천 중 하나여야 합니다.", "sourceRef"))
    elif question_type in QUESTION_TYPES and question_type not in SOURCE_TYPE_CONTRACT[source_dataset]:
        issues.append(_issue("SOURCE_TYPE_INVALID", "원천 데이터와 문항 유형의 조합이 final_datashape 계약에 맞지 않습니다.", "questionTypeCode"))
    if len(mappings) != 1 or unit_id not in known_unit_ids or mappings[0].get("isPrimary") is not True:
        issues.append(_issue("CURRICULUM_MAPPING_INVALID", "18개 소단원 중 정확히 하나의 primary 매핑이 필요합니다.", "curriculumMappings"))

    answer_spec = record.get("answerSpec") if isinstance(record.get("answerSpec"), dict) else {}
    units = [unit for unit in answer_spec.get("units") or [] if isinstance(unit, dict)]

    if question_type == "SHORT_INPUT":
        valid = len(units) == 1 and units[0].get("unitId") == "MAIN" and bool(units[0].get("accepted"))
        if not valid:
            issues.append(_issue("SHORT_INPUT_UNIT_INVALID", "SHORT_INPUT에는 정답이 있는 MAIN answer unit 하나가 필요합니다.", "answerSpec.units"))
    elif question_type == "STEP_FILL":
        blank_ids = [
            str(part.get("blankId") or "")
            for stage in (record.get("problemData") or {}).get("stages") or []
            if isinstance(stage, dict)
            for part in stage.get("contentParts") or []
            if isinstance(part, dict) and part.get("type") == "BLANK"
        ]
        unit_ids = [str(unit.get("unitId") or "") for unit in units]
        if not blank_ids or sorted(blank_ids) != sorted(unit_ids) or len(set(blank_ids)) != len(blank_ids):
            issues.append(_issue("STEP_FILL_UNIT_MISMATCH", "STEP_FILL 빈칸과 answer unit ID가 일대일로 일치해야 합니다.", "problemData.stages"))
    elif question_type == "MULTIPLE_CHOICE":
        valid = (
            len(units) == 1
            and units[0].get("unitId") == "MAIN"
            and units[0].get("unitType") == "CHOICE"
            and bool(units[0].get("accepted"))
        )
        if not valid:
            issues.append(_issue("MULTIPLE_CHOICE_UNIT_INVALID", "MULTIPLE_CHOICE에는 정답이 있는 MAIN CHOICE unit 하나가 필요합니다.", "answerSpec.units"))
    elif question_type == "ESSAY":
        main = next((unit for unit in units if unit.get("unitId") == "MAIN" and unit.get("unitType") == "RUBRIC"), None)
        if not main or not main.get("accepted"):
            issues.append(_issue("ESSAY_MAIN_ANSWER_INVALID", "ESSAY에는 모범답안이 있는 MAIN RUBRIC unit이 필요합니다.", "answerSpec.units"))
        rubric = record.get("rubricItems") or []
        if not rubric or any(not isinstance(item, dict) or not (item.get("criterion") or item.get("description")) for item in rubric):
            issues.append(_issue("ESSAY_RUBRIC_MISSING", "ESSAY에는 유효한 rubric 항목이 필요합니다.", "rubricItems"))

    diagnostic_types = {"MODEL", "INTERPRET", "EXECUTE", "ANSWER"}
    for index, unit in enumerate(units):
        diagnostic_type = unit.get("diagnosticType")
        if question_type == "STEP_FILL" and diagnostic_type not in diagnostic_types:
            issues.append(_issue("DIAGNOSTIC_TYPE_INVALID", "STEP_FILL 빈칸에는 유효한 diagnosticType이 필요합니다.", f"answerSpec.units[{index}].diagnosticType"))
        elif question_type != "STEP_FILL" and diagnostic_type not in (None, ""):
            issues.append(_issue("DIAGNOSTIC_TYPE_NOT_APPLICABLE", "diagnosticType은 STEP_FILL에만 사용할 수 있습니다.", f"answerSpec.units[{index}].diagnosticType"))
        prefix = unit.get("displayPrefix")
        suffix = unit.get("displaySuffix")
        if question_type != "SHORT_INPUT" and (prefix not in (None, "") or suffix not in (None, "")):
            issues.append(_issue("DISPLAY_WRAPPER_INVALID", "표시 prefix/suffix는 SHORT_INPUT에만 사용할 수 있습니다.", f"answerSpec.units[{index}]"))
        raw, normalized = _accepted_value(unit)
        if not raw or not normalized:
            issues.append(_issue("ANSWER_VALUE_MISSING", "answer unit에는 비교 가능한 정답이 필요합니다.", f"answerSpec.units[{index}].accepted"))
            continue
        comparison = {"answer_normalized": normalized, "display_prefix": prefix, "display_suffix": suffix}
        without_wrappers = {**comparison, "display_prefix": None, "display_suffix": None}
        if compare_answer_unit(comparison, raw) != compare_answer_unit(without_wrappers, raw):
            issues.append(_issue("DISPLAY_WRAPPER_AFFECTS_COMPARISON", "표시 prefix/suffix는 채점 결과에 영향을 주면 안 됩니다.", f"answerSpec.units[{index}]"))
    return issues


def validate_canonical_question(record: dict) -> list[dict]:
    """Validate one question against the canonical M1 contract."""
    known_unit_ids = {unit.curriculum_unit_id for unit in curriculum_units()}
    return _validate_canonical_question(record, known_unit_ids)


def _write_jsonl(path: Path, rows: Iterable[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _snake_case(value: str) -> str:
    return re.sub(r"(?<!^)(?=[A-Z])", "_", value).lower()


def _snake_row(row: dict, *, excluded: set[str] | None = None) -> dict:
    excluded = excluded or set()
    return {_snake_case(key): copy.deepcopy(value) for key, value in row.items() if key not in excluded}


def _unit_step_ids(record: dict) -> dict[str, str]:
    result = {}
    for stage in (record.get("problemData") or {}).get("stages") or []:
        if not isinstance(stage, dict):
            continue
        stage_id = str(stage.get("stageId") or "")
        for part in stage.get("contentParts") or []:
            if isinstance(part, dict) and part.get("type") == "BLANK":
                result[str(part.get("blankId") or "")] = stage_id
    return result


def _question_row(record: dict) -> dict:
    mapping = record["curriculumMappings"][0]
    return {
        "question_id": record["recordId"],
        "source_ref": record.get("sourceRef"),
        "source_type": record.get("sourceType"),
        "question_type_code": record.get("questionTypeCode"),
        "sub_unit_id": mapping.get("curriculumUnitId"),
        "prompt_text": record.get("promptText"),
        "semester": record.get("semester"),
        "difficulty": record.get("difficulty"),
        "presentation": record.get("presentation"),
        "source_metadata": copy.deepcopy(record.get("sourceMetadata")),
        "generation_metadata": copy.deepcopy(record.get("generationMetadata")),
    }


def _db_rows(records: list[dict]) -> dict[str, list[dict]]:
    tables = {name: [] for name in DB_TABLES}
    for record in records:
        question_id = record["recordId"]
        question_type = record["questionTypeCode"]
        tables["question"].append(_question_row(record))

        choices = (record.get("choiceOptions") or {}).get("options") or []
        for order, choice in enumerate(choices, 1):
            clean = _snake_row(choice, excluded={"isCorrect", "is_correct", "correct"})
            tables["question_choice"].append({
                "question_id": question_id,
                "choice_id": choice.get("optionId") or choice.get("choiceId") or str(order),
                "label": choice.get("label"),
                "text": choice.get("text"),
                "display_order": choice.get("displayOrder") or order,
                **{key: value for key, value in clean.items() if key not in {"option_id", "choice_id", "label", "text", "display_order"}},
            })

        step_ids = _unit_step_ids(record)
        stages = (record.get("problemData") or {}).get("stages") or [] if question_type == "STEP_FILL" else []
        for order, stage in enumerate(stages, 1):
            tables["question_step"].append({
                "question_id": question_id,
                "step_id": stage.get("stageId"),
                "display_order": stage.get("order") or order,
                "title": stage.get("title"),
                "segments": copy.deepcopy(stage.get("contentParts") or []),
            })

        units = (record.get("answerSpec") or {}).get("units") or []
        for order, unit in enumerate(units, 1):
            raw, normalized = _accepted_value(unit)
            unit_id = str(unit.get("unitId") or "")
            tables["question_answer_unit"].append({
                "question_id": question_id,
                "unit_id": unit_id,
                "step_id": step_ids.get(unit_id) if question_type == "STEP_FILL" else None,
                "display_order": unit.get("displayOrder") or order,
                "unit_type": unit.get("unitType"),
                "answer_type": unit.get("answerType"),
                # diagnostic_type is a STEP_FILL-only diagnostic dimension.
                # Keep the column present for every row so DB staging has a
                # stable shape; non-STEP_FILL rows intentionally use NULL.
                "diagnostic_type": unit.get("diagnosticType") if question_type == "STEP_FILL" else None,
                "compare_method": unit.get("compareMethod"),
                "answer_raw": raw,
                "answer_normalized": normalized,
                "display_prefix": unit.get("displayPrefix") if question_type == "SHORT_INPUT" else None,
                "display_suffix": unit.get("displaySuffix") if question_type == "SHORT_INPUT" else None,
            })

        for order, rubric in enumerate(record.get("rubricItems") or [], 1):
            tables["question_rubric_item"].append({
                "question_id": question_id,
                "rubric_id": rubric.get("rubricId") or str(order),
                "criterion_code": rubric.get("criterionCode"),
                "criterion": rubric.get("criterion") or rubric.get("description"),
                "display_order": rubric.get("displayOrder") or order,
            })

        for order, asset in enumerate(record.get("assets") or [], 1):
            row = _snake_row(asset)
            tables["question_asset"].append({
                "question_id": question_id,
                "asset_id": asset.get("assetId") or asset.get("blockId") or str(order),
                "display_order": asset.get("displayOrder") or order,
                **{key: value for key, value in row.items() if key not in {"asset_id", "display_order"}},
            })
    return tables


def _reference_rows(reference_dir: Path) -> list[dict]:
    rows = []
    for dataset in ("30", "110"):
        path = Path(reference_dir) / f"{dataset}.jsonl"
        if not path.exists():
            continue
        rows.extend(
            json.loads(line)
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip()
        )
    return rows


def _replacement_refs(record: dict) -> list[str]:
    refs = []
    for key in ("replacesSourceRef", "replacementForSourceRef"):
        if record.get(key):
            refs.append(str(record[key]))
    for container_name in ("sourceMetadata", "generationMetadata"):
        container = record.get(container_name) if isinstance(record.get(container_name), dict) else {}
        for key in ("replacesSourceRef", "replacementForSourceRef"):
            if container.get(key):
                refs.append(str(container[key]))
    return refs


def compare_reference_coverage(records: list[dict], reference_dir: Path) -> dict:
    """Compare source reference coverage without using reference rows as build inputs."""
    reference_refs = {str(row.get("sourceRef")) for row in _reference_rows(Path(reference_dir)) if row.get("sourceRef")}
    accepted = [record for record in records if record.get("status") != "REJECTED"]
    direct = {str(record.get("sourceRef")) for record in accepted if record.get("sourceRef")}
    preserved_refs = reference_refs & direct

    replacements = {}
    for record in accepted:
        for source_ref in _replacement_refs(record):
            if source_ref in reference_refs and source_ref not in preserved_refs:
                replacements[source_ref] = {
                    "sourceRef": source_ref,
                    "recordId": record.get("recordId") or record.get("recordKey") or record.get("sourceRef"),
                }

    rejected = {}
    for record in records:
        source_ref = str(record.get("sourceRef") or "")
        if source_ref in reference_refs and record.get("status") == "REJECTED" and record.get("issues"):
            rejected[source_ref] = {"sourceRef": source_ref, "issues": copy.deepcopy(record["issues"])}

    explained = preserved_refs | set(replacements) | set(rejected)
    return {
        "referenceTotal": len(reference_refs),
        "preserved": len(preserved_refs),
        "replaced": [replacements[key] for key in sorted(replacements)],
        "rejectedWithReason": [rejected[key] for key in sorted(rejected)],
        "unexplainedMissing": sorted(reference_refs - explained),
    }


def export_dataset(
    records: list[dict],
    curriculum_rows: list[dict],
    output_dir: Path,
    *,
    reference_dir: Path | None = None,
) -> dict:
    """Write canonical, source-compatible and ERD staging views for validated records."""
    records = copy.deepcopy(records)
    curriculum_rows = copy.deepcopy(curriculum_rows)
    known_unit_ids = {
        str(row.get("curriculum_unit_id") or row.get("curriculumUnitId") or "")
        for row in curriculum_rows
        if isinstance(row, dict)
    }
    known_unit_ids.discard("")
    if not known_unit_ids:
        known_unit_ids = {unit.curriculum_unit_id for unit in curriculum_units()}

    validation = []
    seen_ids = set()
    for index, record in enumerate(records):
        record_id = str(record.get("recordId") or "")
        issues = _validate_canonical_question(record, known_unit_ids)
        if not record_id:
            issues.append(_issue("RECORD_ID_MISSING", "recordId가 필요합니다.", "recordId"))
        elif record_id in seen_ids:
            issues.append(_issue("RECORD_ID_DUPLICATE", "recordId는 중복될 수 없습니다.", "recordId"))
        seen_ids.add(record_id)
        if issues:
            validation.append({"index": index, "recordId": record_id or None, "sourceRef": record.get("sourceRef"), "issues": issues})
    if validation:
        codes = ", ".join(issue["code"] for row in validation for issue in row["issues"])
        raise ValueError(f"canonical validation failed: {codes}")

    output_dir = Path(output_dir)
    _write_jsonl(output_dir / "canonical/questions.jsonl", records)
    _write_jsonl(output_dir / "canonical/curriculum_units.jsonl", curriculum_rows)
    guides = [
        {"recordId": record["recordId"], **copy.deepcopy(record["learningGuide"])}
        for record in records
        if isinstance(record.get("learningGuide"), dict)
    ]
    _write_jsonl(output_dir / "canonical/learning_guides.jsonl", guides)

    by_dataset = {dataset: [] for dataset in SOURCE_DATASETS}
    for record in records:
        dataset = str(record.get("sourceRef") or "").partition(":")[0]
        if dataset in by_dataset:
            by_dataset[dataset].append(record)
    for dataset, rows in by_dataset.items():
        _write_jsonl(output_dir / f"final_datashape/{dataset}.jsonl", rows)

    tables = _db_rows(records)
    for name, rows in tables.items():
        _write_jsonl(output_dir / f"db_staging/{name}.jsonl", rows)

    manifest = {
        "questionCount": len(records),
        "curriculumUnitCount": len(curriculum_rows),
        "countsByType": dict(sorted(Counter(record["questionTypeCode"] for record in records).items())),
        "countsByDataset": {dataset: len(rows) for dataset, rows in by_dataset.items()},
        "dbTableCounts": {name: len(rows) for name, rows in tables.items()},
        "outputs": {
            "canonicalQuestions": "canonical/questions.jsonl",
            "finalDatashape": {dataset: f"final_datashape/{dataset}.jsonl" for dataset in SOURCE_DATASETS},
            "dbStagingDirectory": "db_staging",
        },
    }
    if reference_dir is not None:
        manifest["referenceCoverage"] = compare_reference_coverage(records, Path(reference_dir))
    return manifest
