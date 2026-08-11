from __future__ import annotations

import argparse
import copy
import json
import re
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

from .asset_packaging import SourceMediaIndex, package_record_assets
from .catalog import curriculum_units
from .content_normalization import is_objective_source, normalize_content_structure
from .exporter import export_dataset, validate_canonical_question
from .quality_transform import transform_quality_records
from .raw_sources import iter_raw_json
from .step_fill import normalize_blank_answer


FINALIZATION_VERSION = "m1-finalization-v1"
NUMERIC = re.compile(r"^[+-]?(?:\d+(?:\.\d+)?|\d+/\d+)$")
SYMBOLIC_MARKERS = re.compile(r"[=<>+*/^]|\\(?:frac|sqrt)|[A-Za-z]")


def _read_jsonl(path: Path) -> list[dict]:
    if not Path(path).exists():
        return []
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def _write_jsonl(path: Path, rows: Iterable[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def infer_answer_type(value: Any) -> str:
    normalized = normalize_blank_answer({"raw": value}).get("normalized", "")
    if NUMERIC.fullmatch(normalized):
        return "NUMERIC"
    if SYMBOLIC_MARKERS.search(normalized):
        return "SYMBOLIC_EQUIVALENCE"
    return "TEXT_SET"


def _normalize_answer(answer: Any, answer_type_hint: str | None = None) -> dict:
    item = answer if isinstance(answer, dict) else {"raw": answer}
    normalized = normalize_blank_answer(item)
    result = {**item, **normalized}
    result["answerType"] = item.get("answerType") or answer_type_hint or infer_answer_type(normalized["normalized"])
    return result


def infer_compare_method(question_type: str, unit: dict) -> str:
    if question_type == "MULTIPLE_CHOICE" or unit.get("unitType") == "CHOICE":
        return "CHOICE"
    if question_type == "ESSAY" or unit.get("unitType") == "RUBRIC":
        return "RUBRIC"
    return {"NUMERIC": "VALUE", "VALUE": "VALUE", "SYMBOLIC_EQUIVALENCE": "SUBST",
            "SET": "SET", "TEXT_SET": "SET", "EXACT": "EXACT"}.get(
                str(unit.get("answerType") or "").upper(), "EXACT")


def finalize_record(record: dict) -> dict:
    output = normalize_content_structure(record)
    source_ref = str(output.get("sourceRef") or "")
    output["recordId"] = output.get("recordId") or (
        f"generated:step-fill:{source_ref.partition(':')[2]}"
        if output.get("questionTypeCode") == "STEP_FILL" else f"source:{source_ref}"
    )
    output["questionTypeCode"] = output.get("questionTypeCode") or "STANDARD"
    metadata = output.get("sourceMetadata") or {}
    source_ref_dataset = source_ref.partition(":")[0]
    question_type = str(metadata.get("questionType1") or "")
    answer_type_hint = None
    if source_ref_dataset == "30":
        answer_type_hint = "OPTION_SET" if any(token in question_type for token in ("객관", "선택")) else "TEXT_SET"
    answer_spec = output.get("answerSpec")
    if isinstance(answer_spec, dict):
        answer_spec["finalAnswer"] = [_normalize_answer(item, answer_type_hint) for item in answer_spec.get("finalAnswer") or []]
        units = []
        for unit in answer_spec.get("units") or []:
            normalized_unit = copy.deepcopy(unit)
            normalized_unit["accepted"] = [_normalize_answer(item, answer_type_hint) for item in unit.get("accepted") or []]
            if not normalized_unit.get("answerType") and normalized_unit["accepted"]:
                normalized_unit["answerType"] = normalized_unit["accepted"][0]["answerType"]
            normalized_unit["compareMethod"] = infer_compare_method(output["questionTypeCode"], normalized_unit)
            units.append(normalized_unit)
        answer_spec["units"] = units
    output["presentation"] = output.get("presentation") or ("WITH_FIGURE" if output.get("assets") else "TEXT_ONLY")
    dataset = source_ref.partition(":")[0]
    output["generationMetadata"] = output.get("generationMetadata") or {
        "source": f"AIHUB_{dataset}", "sourceContract": f"RAW_{dataset}",
    }
    output.pop("pipelineStage", None)
    return output


def merge_step_fill_results(base_rows: list[dict], accepted_rows: list[dict], rejected_rows: list[dict]) -> tuple[list[dict], list[dict]]:
    accepted = {row["sourceRef"]: row for row in accepted_rows if row.get("sourceRef")}
    rejected = {row["sourceRef"]: row for row in rejected_rows if row.get("sourceRef")}
    base_refs = {row.get("sourceRef") for row in base_rows}
    merged = []
    for base in base_rows:
        source_ref = base.get("sourceRef")
        if is_objective_source(base):
            continue
        if base.get("questionTypeCode") == "ESSAY":
            merged.append(copy.deepcopy(base))
            continue
        if source_ref in rejected:
            continue
        merged.append(copy.deepcopy(accepted.get(source_ref, base)))
    merge_rejects = [copy.deepcopy(row) for row in rejected.values()]
    for source_ref in sorted(set(accepted) - base_refs):
        merge_rejects.append({"sourceRef": source_ref, "status": "REJECTED", "issues": [{
            "code": "BASE_RECORD_MISSING", "message": "LearningGuide 기반 문항에서 sourceRef를 찾을 수 없습니다.", "path": "sourceRef"
        }]})
    return merged, sorted(merge_rejects, key=lambda row: row["sourceRef"])


def validate_final_record(record: dict) -> list[dict]:
    issues = []
    for field in ("recordId", "sourceRef", "sourceDatasetCode", "promptText", "questionTypeCode"):
        if not record.get(field):
            issues.append({"code": "REQUIRED_FIELD_MISSING", "message": f"{field} 값이 필요합니다.", "path": field})
    if not record.get("curriculumMappings"):
        issues.append({"code": "CURRICULUM_MAPPING_MISSING", "message": "교육과정 매핑이 필요합니다.", "path": "curriculumMappings"})
    if not record.get("learningGuide"):
        issues.append({"code": "LEARNING_GUIDE_MISSING", "message": "LearningGuide가 필요합니다.", "path": "learningGuide"})
    answer_spec = record.get("answerSpec")
    if isinstance(answer_spec, dict):
        for index, answer in enumerate(answer_spec.get("finalAnswer") or []):
            if not answer.get("answerType"):
                issues.append({"code": "ANSWER_TYPE_MISSING", "message": "answerType이 필요합니다.", "path": f"answerSpec.finalAnswer[{index}]"})
        for index, unit in enumerate(answer_spec.get("units") or []):
            if not unit.get("answerType"):
                issues.append({"code": "ANSWER_TYPE_MISSING", "message": "answerType이 필요합니다.", "path": f"answerSpec.units[{index}]"})
    if record.get("questionTypeCode") == "STEP_FILL":
        blank_ids = {part.get("blankId") for stage in (record.get("problemData") or {}).get("stages") or []
                     for part in stage.get("contentParts") or [] if part.get("type") == "BLANK"}
        unit_ids = {unit.get("unitId") for unit in (answer_spec or {}).get("units") or []}
        if blank_ids != unit_ids:
            issues.append({"code": "STEP_FILL_UNIT_MISMATCH", "message": "빈칸과 정답 unitId가 일치하지 않습니다.", "path": "problemData"})
    return issues


def _relational_rows(records: list[dict]) -> dict[str, list[dict]]:
    tables = {name: [] for name in (
        "questions", "content_blocks", "assets", "question_curriculum_mappings", "learning_guides",
        "answer_units", "accepted_answers", "problem_stages", "problem_stage_parts",
    )}
    for record in records:
        record_id = record["recordId"]
        metadata = record.get("sourceMetadata") or {}
        tables["questions"].append({
            "record_id": record_id, "source_ref": record["sourceRef"], "source_type": record.get("sourceType"),
            "source_dataset_code": record.get("sourceDatasetCode"), "question_type": record.get("questionTypeCode"),
            "prompt_text": record.get("promptText"), "difficulty": record.get("difficulty"), "semester": record.get("semester"),
            "source_topic_key": metadata.get("sourceTopicKey"), "source_metadata": metadata,
            "choice_options": record.get("choiceOptions"), "generation_metadata": record.get("generationMetadata"),
        })
        for order, block in enumerate(record.get("contentBlocks") or []):
            tables["content_blocks"].append({"record_id": record_id, "block_id": block.get("blockId"), "display_order": order, **block})
        for order, asset in enumerate(record.get("assets") or []):
            tables["assets"].append({"record_id": record_id, "display_order": order, **asset})
        for order, mapping in enumerate(record.get("curriculumMappings") or []):
            tables["question_curriculum_mappings"].append({"record_id": record_id, "mapping_order": order, **mapping})
        guide = record.get("learningGuide")
        if isinstance(guide, dict):
            tables["learning_guides"].append({"record_id": record_id, **guide})
        answer_spec = record.get("answerSpec") or {}
        for order, answer in enumerate(answer_spec.get("finalAnswer") or []):
            tables["accepted_answers"].append({"record_id": record_id, "unit_id": None, "answer_order": order,
                                                "answer_type": answer.get("answerType"), "raw": answer.get("raw"),
                                                "normalized": answer.get("normalized"), "latex": answer.get("latex")})
        for unit_order, unit in enumerate(answer_spec.get("units") or []):
            unit_id = unit.get("unitId")
            tables["answer_units"].append({"record_id": record_id, "unit_key": unit_id, "unit_order": unit_order,
                                            **{key: value for key, value in unit.items() if key != "accepted"}})
            for answer_order, answer in enumerate(unit.get("accepted") or []):
                tables["accepted_answers"].append({"record_id": record_id, "unit_id": unit_id,
                                                    "answer_order": answer_order, "answer_type": unit.get("answerType") or answer.get("answerType"),
                                                    "raw": answer.get("raw"), "normalized": answer.get("normalized"), "latex": answer.get("latex")})
        for stage_order, stage in enumerate((record.get("problemData") or {}).get("stages") or []):
            stage_id = stage.get("stageId")
            tables["problem_stages"].append({"record_id": record_id, "stage_id": stage_id, "stage_order": stage_order,
                                              **{key: value for key, value in stage.items() if key != "contentParts"}})
            for part_order, part in enumerate(stage.get("contentParts") or []):
                tables["problem_stage_parts"].append({"record_id": record_id, "stage_id": stage_id,
                                                       "part_order": part_order, **part})
    return tables


def _source_raw_index(roots: dict[str, Path], wanted_refs: set[str]) -> dict[str, tuple[dict, str]]:
    index = {}
    for dataset, root in roots.items():
        wanted_ids = {ref.partition(":")[2] for ref in wanted_refs if ref.startswith(f"{dataset}:")}
        for source_file, raw in iter_raw_json(root):
            if dataset == "30":
                source_id = str((raw.get("source_data_info") or {}).get("source_data_name") or Path(source_file).stem)
            else:
                source_id = str(raw.get("id") or Path(source_file).stem)
            if source_id in wanted_ids and f"{dataset}:{source_id}" not in index:
                index[f"{dataset}:{source_id}"] = (raw, source_file)
    return index


def _package_all_assets(rows: list[dict], roots: dict[str, Path], output_dir: Path) -> tuple[list[dict], dict[str, list[dict]]]:
    wanted_refs = {str(row.get("sourceRef") or "") for row in rows}
    raw_index = _source_raw_index(roots, wanted_refs)
    media = {dataset: SourceMediaIndex(root) for dataset, root in roots.items()}
    packaged, issues_by_ref = [], {}
    for row in rows:
        source_ref = row.get("sourceRef", "")
        dataset = source_ref.partition(":")[0]
        raw_entry = raw_index.get(source_ref)
        if not raw_entry:
            packaged.append(row)
            issues_by_ref[source_ref] = [{"code": "SOURCE_RECORD_MISSING", "message": "자산 생성용 원천 JSON을 찾을 수 없습니다.", "path": source_ref}]
            continue
        raw, _ = raw_entry
        source_name = (f"{(raw.get('source_data_info') or {}).get('source_data_name', '')}.png"
                       if dataset == "30" else str(raw.get("question_filename") or ""))
        result, issues = package_record_assets(row, raw, dataset, media[dataset].get(source_name), output_dir)
        packaged.append(result)
        if issues:
            issues_by_ref[source_ref] = issues
    return packaged, issues_by_ref


def run_finalization(guided_path: Path, accepted_path: Path, rejected_path: Path, output_dir: Path, *,
                     source30: Path | None = None, source110: Path | None = None,
                     source111: Path | None = None, essay_limit: int = 20,
                     seed: int = 20260810) -> dict:
    output_dir = Path(output_dir)
    merged, step_rejects = merge_step_fill_results(_read_jsonl(guided_path), _read_jsonl(accepted_path), _read_jsonl(rejected_path))
    roots = {dataset: Path(root) for dataset, root in (("30", source30), ("110", source110), ("111", source111)) if root}
    asset_issues = {}
    if roots:
        merged, asset_issues = _package_all_assets(merged, roots, output_dir)
    finalized = [finalize_record(row) for row in merged]
    quality_rows, quality_rejects, quality_stats = transform_quality_records(
        finalized, essay_limit=essay_limit, seed=seed,
    )
    accepted, validation_rejects = [], []
    for row in quality_rows:
        final = finalize_record(row)
        row_asset_issues = asset_issues.get(final.get("sourceRef", ""), []) if final.get("assets") else []
        issues = [*row_asset_issues, *validate_final_record(final), *validate_canonical_question(final)]
        if issues:
            validation_rejects.append({"sourceRef": final.get("sourceRef"), "recordId": final.get("recordId"),
                                       "status": "REJECTED", "issues": issues})
        else:
            accepted.append(final)
    all_rejects = sorted([*step_rejects, *quality_rejects, *validation_rejects],
                         key=lambda row: (row.get("sourceRef", ""), row.get("recordId", "")))
    _write_jsonl(output_dir / "final_questions.jsonl", accepted)
    _write_jsonl(output_dir / "final_questions_rejected.jsonl", all_rejects)
    tables = _relational_rows(accepted)
    for name, rows in tables.items():
        _write_jsonl(output_dir / "load" / f"{name}.jsonl", rows)
    export_manifest = export_dataset(
        accepted,
        [unit.to_dict() for unit in curriculum_units()],
        output_dir,
    )
    manifest = {
        "finalizationVersion": FINALIZATION_VERSION, "generatedAt": datetime.now(timezone.utc).isoformat(),
        "counts": {"accepted": len(accepted), "rejected": len(all_rejects),
                   "byDataset": dict(Counter(row["sourceRef"].partition(":")[0] for row in accepted))},
        "tables": {name: len(rows) for name, rows in tables.items()},
        "qualityTransform": quality_stats,
        "exports": {**export_manifest, "validationRejected": []},
        "outputs": {"accepted": "final_questions.jsonl", "rejected": "final_questions_rejected.jsonl", "loadDirectory": "load"},
    }
    (output_dir / "finalization_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return manifest


def main() -> None:
    parser = argparse.ArgumentParser(description="LearningGuide와 STEP_FILL 결과를 최종 적재 계약으로 조립합니다")
    parser.add_argument("--guided", type=Path, required=True)
    parser.add_argument("--step-fill-accepted", type=Path, required=True)
    parser.add_argument("--step-fill-rejected", type=Path, required=True)
    parser.add_argument("--source-30", type=Path)
    parser.add_argument("--source-110", type=Path)
    parser.add_argument("--source-111", type=Path)
    parser.add_argument("--essay-limit", type=int, default=20)
    parser.add_argument("--seed", type=int, default=20260810)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(run_finalization(args.guided, args.step_fill_accepted, args.step_fill_rejected, args.output,
                                      source30=args.source_30, source110=args.source_110, source111=args.source_111,
                                      essay_limit=args.essay_limit, seed=args.seed),
                     ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
