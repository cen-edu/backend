"""Raw-source-only build entry point for the M1 question-bank package."""

from __future__ import annotations

import argparse
import copy
import json
import logging
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

from .derivations import resolve_essay_cache, sample_essay_candidates, select_probability_step_fill
from .exporter import compare_reference_coverage
from .finalization import run_finalization
from .pipeline import run_pipeline
from .preflight import discover_input_layout, validate_input_layout
from .step_fill_async import execute_generation
from .step_fill_repair import CACHE_OVERLAY_FIELDS, repair_step_fill_record, resolve_step_fill_cache

LOGGER = logging.getLogger("m1-question-build")

def _log_stage(name: str, started: float, **counts: object) -> None:
    details = " ".join(f"{key}={value}" for key, value in counts.items())
    LOGGER.info("[%s] 완료 (%.1fs)%s", name, time.monotonic() - started, f" {details}" if details else "")


def _read_jsonl(path: Path | None) -> list[dict]:
    if path is None or not Path(path).is_file():
        return []
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line.strip()]


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _source_count(root: Path) -> int:
    return sum(1 for path in root.rglob("*") if path.is_file() and path.suffix.lower() in {".json", ".zip"})


def _overlay(base: dict, cached: dict) -> dict:
    output = copy.deepcopy(base)
    for field in CACHE_OVERLAY_FIELDS:
        if field in cached:
            output[field] = copy.deepcopy(cached[field])
    return output


def _resolve_111_step_cache(rows: list[dict], cached_rows: list[dict]) -> tuple[list[dict], list[dict], list[dict]]:
    caches: dict[str, list[dict]] = {}
    for cached in cached_rows:
        if isinstance(cached, dict) and str(cached.get("sourceRef") or "").startswith("111:"):
            caches.setdefault(str(cached["sourceRef"]), []).append(cached)
    accepted, candidates, rejected = [], [], []
    for row in rows:
        repaired = next((fixed for cached in caches.get(str(row.get("sourceRef") or ""), [])
                         if (fixed := repair_step_fill_record(cached)[0]) is not None), None)
        if repaired is None:
            candidates.append(copy.deepcopy(row))
        else:
            accepted.append(_overlay(row, repaired))
    return accepted, candidates, rejected


def _generation_payloads(rows: list[dict]) -> list[dict]:
    payloads = []
    for row in rows:
        evidence = (row.get("sourceMetadata") or {}).get("sourceEvidence") or {}
        model_answer = str(evidence.get("modelAnswerText") or "").strip()
        final_answer = str(evidence.get("finalAnswer") or "").strip()
        if not model_answer or not final_answer:
            continue
        payload = copy.deepcopy(row)
        payload["stepFillEvidence"] = {
            "mode": "SOURCE_GROUNDED",
            "modelAnswerText": model_answer,
            "finalAnswer": final_answer,
            "sourceSpans": [
                {"spanId": "Q-1", "text": str(row.get("promptText") or "")},
                {"spanId": "A-1", "text": model_answer},
                {"spanId": "F-A1", "text": final_answer},
            ],
        }
        payloads.append(payload)
    return payloads


def _rows_by_dataset(rows: list[dict], dataset: str) -> list[dict]:
    return [row for row in rows if str(row.get("sourceRef") or "").startswith(f"{dataset}:")]


def _rejection(source_ref: str, code: str, message: str, *, status: str = "REJECTED", path: str = "build") -> dict:
    return {"sourceRef": source_ref, "status": status,
            "issues": [{"code": code, "message": message, "path": path}]}


def _dedupe_rows(rows: list[dict]) -> list[dict]:
    unique = {}
    for row in rows:
        key = (str(row.get("sourceRef") or ""), str(row.get("status") or ""),
               json.dumps(row.get("issues") or [], ensure_ascii=False, sort_keys=True))
        unique.setdefault(key, row)
    return [unique[key] for key in sorted(unique)]


def run_build(data_root: Path, output_dir: Path, *, allow_api_generation: bool = False,
              essay_limit: int = 20, seed: int = 20260810,
              reference_final_datashape: Path | None = None) -> dict:
    """Build canonical/final_datashape/DB staging outputs from raw 30, 110, and 111 sources."""
    started = time.monotonic()
    data_root, output_dir = Path(data_root), Path(output_dir)
    LOGGER.info("빌드 시작 data_root=%s output=%s api_generation=%s", data_root, output_dir, allow_api_generation)
    layout = discover_input_layout(data_root)
    issues = validate_input_layout(layout)
    if issues:
        raise ValueError(f"invalid input layout: {issues}")
    if output_dir.resolve().is_relative_to(data_root.resolve()):
        raise ValueError("output_dir must not be located inside data_root")

    intermediate = output_dir / "intermediate"
    reports = output_dir / "reports"
    generation_dir = output_dir / "generation"
    LOGGER.info("[1/8] 원천 입력 검증 완료; 30=%d 110=%d 111=%d 파일", *[_source_count(getattr(layout, f"source{dataset}")) for dataset in ("30", "110", "111")])
    stage_started = time.monotonic()
    LOGGER.info("[2/8] 30·110·111 정규화, 18개 소단원 분류, LearningGuide 처리 시작")
    pipeline_manifest = run_pipeline(layout.source30, layout.source110, layout.source111, intermediate / "01_pipeline")
    guided = _read_jsonl(intermediate / "01_pipeline" / "03_learning_guided_questions.jsonl")
    mapped = _read_jsonl(intermediate / "01_pipeline" / "02_curriculum_mapped_questions.jsonl")
    guided_refs = {str(row.get("sourceRef") or "") for row in guided}
    classification_rejects = [
        _rejection(str(row.get("sourceRef") or ""), "CURRICULUM_MAPPING_UNRESOLVED",
                   "정확히 하나의 소단원에 분류되지 않아 최종 문항에서 제외했습니다.", path="curriculumMappings")
        for row in mapped if str(row.get("sourceRef") or "") not in guided_refs
    ]
    # Keep unresolved 30 rows until the common quality transform. Composite
    # short-answer sources can be safely split there before every sink.
    rows30 = _rows_by_dataset(guided, "30")
    type_rejects = []
    rows110 = _rows_by_dataset(guided, "110")
    rows111 = _rows_by_dataset(guided, "111")
    _write_jsonl(intermediate / "02_normalized_and_mapped.jsonl", guided)
    _log_stage("2/8 pipeline", stage_started, normalized=len(guided), mapped=len(mapped))

    cached_steps = _read_jsonl(layout.step_fill_accepted)
    cached110 = _rows_by_dataset(cached_steps, "110")
    cached111 = _rows_by_dataset(cached_steps, "111")
    LOGGER.info("[3/8] STEP_FILL 캐시 검증 시작 cache_rows=%d cache110=%d cache111=%d", len(cached_steps), len(cached110), len(cached111))
    accepted110, candidates110, cache_rejects = resolve_step_fill_cache(rows110, cached110)
    LOGGER.info("[4/8] 110·111 fingerprint 중복 비교 시작 baseline110=%d candidates111=%d; 대용량 입력에서는 시간이 걸릴 수 있습니다", len(rows110), len(rows111))
    stage_started = time.monotonic()
    probability_rows, duplicate_rows = select_probability_step_fill(rows111, rows110)
    accepted111, candidates111, probability_rejects = _resolve_111_step_cache(probability_rows, cached111)
    candidates = [*candidates110, *candidates111]
    _write_jsonl(intermediate / "03_step_fill_accepted.jsonl", [*accepted110, *accepted111])
    _write_jsonl(intermediate / "04_step_fill_duplicate_audit.jsonl", duplicate_rows)
    _write_jsonl(intermediate / "05_step_fill_generation_candidates.jsonl", candidates)
    _log_stage("4/8 STEP_FILL 중복/캐시", stage_started, accepted110=len(accepted110), accepted111=len(accepted111), duplicates=len(duplicate_rows), candidates=len(candidates))

    duplicate_rejects = [
        _rejection(str(row.get("sourceRef") or ""), "DUPLICATE_110_CONTENT",
                   "110번과 동일한 자료와 가능성 문항이므로 111 STEP_FILL 후보에서 제외했습니다.", path="stepFillDuplicateEvidence")
        for row in duplicate_rows
    ]
    repair_counts = {
        "cacheHits": len(accepted111) + sum(1 for row in accepted110 if row.get("generationMetadata", {}).get("generationMethod") != "DETERMINISTIC_SOURCE_COMPILE"),
        "deterministicAccepted": sum(1 for row in accepted110 if row.get("generationMetadata", {}).get("generationMethod") == "DETERMINISTIC_SOURCE_COMPILE"),
    }
    generation = {"apiCalls": 0, **repair_counts, "generationCandidates": len(candidates), "estimatedNewCalls": len(candidates)}
    _write_jsonl(generation_dir / "step_fill_candidates.jsonl", candidates)
    api_rejects, pending_generation = [], []
    if allow_api_generation and candidates:
        LOGGER.info("[5/8] API 생성 시작 payload 후보를 준비합니다")
        payloads = _generation_payloads(candidates)
        payload_refs = {str(row.get("sourceRef") or "") for row in payloads}
        api_rejects.extend(
            _rejection(str(row.get("sourceRef") or ""), "SOURCE_ANSWER_MISSING",
                       "API 생성 근거가 되는 원천 풀이 또는 최종 답이 없습니다.", path="sourceMetadata.sourceEvidence")
            for row in candidates if str(row.get("sourceRef") or "") not in payload_refs
        )
        api_generation = execute_generation(payloads, generation_dir, allow_api_generation=True)
        generation = {**generation, **api_generation, **repair_counts,
                      "generationCandidates": len(candidates), "estimatedNewCalls": len(payloads),
                      "apiCalls": api_generation.get("apiCalls", 0)}
        generated = _read_jsonl(generation_dir / "step_fill_accepted.jsonl")
        api_rejects.extend(_read_jsonl(generation_dir / "step_fill_rejected.jsonl"))
        accepted110.extend(_rows_by_dataset(generated, "110"))
        accepted111.extend(_rows_by_dataset(generated, "111"))
        _write_jsonl(generation_dir / "step_fill_candidates.jsonl", candidates)
    elif candidates:
        LOGGER.info("[5/8] API 생성 비활성화; 후보를 generation/step_fill_candidates.jsonl에 기록합니다 candidates=%d", len(candidates))
        pending_generation = [
            _rejection(str(row.get("sourceRef") or ""), "API_GENERATION_NOT_APPROVED",
                       "승인된 STEP_FILL 캐시가 없으며 --allow-api-generation 없이 API를 호출하지 않았습니다.",
                       status="PENDING_GENERATION", path="generation")
            for row in candidates
        ]

    LOGGER.info("[6/8] ESSAY 후보 샘플링 시작 limit=%d seed=%d", essay_limit, seed)
    step_fill_refs = {
        str(row.get("sourceRef")) for row in accepted111 if row.get("sourceRef")
    }
    essay_source_rows = [
        row for row in rows111
        if str(row.get("sourceRef")) not in step_fill_refs
    ]
    essay_candidates = sample_essay_candidates(essay_source_rows, limit=essay_limit, seed=seed)
    essays, essay_rejects = resolve_essay_cache(essay_candidates, _read_jsonl(layout.essay_accepted))
    _write_jsonl(intermediate / "07_essay_candidates.jsonl", essay_candidates)
    _write_jsonl(intermediate / "08_essay_accepted.jsonl", essays)
    _write_jsonl(generation_dir / "essay_candidates.jsonl", essay_candidates)
    LOGGER.info("[6/8] ESSAY 후보 완료 candidates=%d accepted_cache=%d", len(essay_candidates), len(essays))

    production = [*rows30, *accepted110, *accepted111, *essays]
    production.sort(key=lambda row: (str(row.get("sourceRef") or ""), str(row.get("recordId") or "")))
    accepted_path, rejected_path = intermediate / "09_step_fill_accepted.jsonl", intermediate / "09_step_fill_rejected.jsonl"
    _write_jsonl(accepted_path, [*accepted110, *accepted111])
    _write_jsonl(rejected_path, [*cache_rejects, *probability_rejects, *essay_rejects, *api_rejects])
    production_path = intermediate / "10_production_questions.jsonl"
    _write_jsonl(production_path, production)
    LOGGER.info("[7/8] canonical/final_datashape/db_staging export 시작 production=%d", len(production))
    final_manifest = run_finalization(production_path, accepted_path, rejected_path, output_dir,
                                      source30=layout.source30, source110=layout.source110, source111=layout.source111,
                                      essay_limit=essay_limit, seed=seed)

    final_rejects = _read_jsonl(output_dir / "final_questions_rejected.jsonl")
    canonical_rejects = final_manifest["exports"].get("validationRejected") or []
    all_rejections = _dedupe_rows([
        *classification_rejects, *type_rejects, *duplicate_rejects, *cache_rejects, *probability_rejects,
        *essay_rejects, *api_rejects, *final_rejects,
        *[{"sourceRef": row.get("sourceRef"), "status": "REJECTED", "issues": row.get("issues") or []} for row in canonical_rejects],
        *pending_generation,
    ])
    rejected_count = sum(row.get("status") != "PENDING_GENERATION" for row in all_rejections)
    counts_by_type = final_manifest["exports"]["countsByType"]
    report = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "status": "READY_WITH_GENERATION_CANDIDATES" if pending_generation else "COMPLETE",
        "inputFileCounts": {dataset: _source_count(getattr(layout, f"source{dataset}")) for dataset in ("30", "110", "111")},
        "accepted": final_manifest["exports"]["questionCount"], "rejected": rejected_count,
        "pendingGeneration": len(pending_generation),
        "repair": {"cacheHits": generation["cacheHits"], "deterministicStepFill": generation["deterministicAccepted"]},
        "countsByType": counts_by_type,
        "countsByDataset": final_manifest["exports"]["countsByDataset"],
        "countsBySubUnit": dict(Counter(
            row["curriculumMappings"][0]["curriculumUnitId"]
            for row in _read_jsonl(output_dir / "canonical/questions.jsonl") if row.get("curriculumMappings")
        )),
        "duplicateDecisions": {"excludedExact": len(duplicate_rows), "review": sum(bool(row.get("stepFillSimilarityCandidates")) for row in probability_rows)},
        "generationCandidates": generation["generationCandidates"], "apiCalls": generation["apiCalls"],
        "curriculumUnitCount": final_manifest["exports"]["curriculumUnitCount"], "pipeline": pipeline_manifest,
    }
    _write_jsonl(reports / "classification_audit.jsonl", [
        {"sourceRef": row.get("sourceRef"), "classification": (row.get("curriculumMappings") or [{}])[0].get("evidence", {}).get("classification")}
        for row in guided
    ] + classification_rejects)
    _write_jsonl(reports / "duplicate_review.jsonl", [*duplicate_rows, *[
        {"sourceRef": row.get("sourceRef"), "similarityCandidates": row.get("stepFillSimilarityCandidates")}
        for row in probability_rows if row.get("stepFillSimilarityCandidates")
    ]])
    _write_jsonl(reports / "rejected.jsonl", all_rejections)
    original_by_ref = {str(row.get("sourceRef") or ""): row for row in [*rows110, *probability_rows]}
    def repair_audit(row: dict) -> dict:
        source_ref = str(row.get("sourceRef") or "")
        before = original_by_ref.get(source_ref) or {}
        method = (row.get("generationMetadata") or {}).get("generationMethod") or "CACHE_REPAIR"
        return {
            "sourceRef": source_ref,
            "before": {"questionTypeCode": before.get("questionTypeCode"), "answerUnitCount": len((before.get("answerSpec") or {}).get("units") or [])},
            "after": {"questionTypeCode": row.get("questionTypeCode"), "answerUnitCount": len((row.get("answerSpec") or {}).get("units") or [])},
            "appliedRules": [method],
        }
    _write_jsonl(reports / "repair_log.jsonl", [repair_audit(row) for row in [*accepted110, *accepted111]])
    _write_jsonl(generation_dir / "generation_summary.jsonl", [{**generation, "status": report["status"]}])
    (generation_dir / "generation_summary.json").write_text(json.dumps({**generation, "status": report["status"]}, ensure_ascii=False, indent=2), encoding="utf-8")
    if reference_final_datashape is not None:
        canonical_rows = _read_jsonl(output_dir / "canonical/questions.jsonl")
        report["referenceCoverage"] = compare_reference_coverage(canonical_rows, Path(reference_final_datashape))
        reports.mkdir(parents=True, exist_ok=True)
        (reports / "regression_coverage.json").write_text(json.dumps(report["referenceCoverage"], ensure_ascii=False, indent=2), encoding="utf-8")
    if (output_dir / "final_questions_rejected.jsonl").exists() and "rejected" not in report:
        raise RuntimeError("rejected count is required when rejected output exists")
    reports.mkdir(parents=True, exist_ok=True)
    (reports / "quality_report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (reports / "run_summary.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (output_dir / "build_manifest.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    _log_stage("8/8 빌드 완료", started, accepted=report["accepted"], rejected=report["rejected"], apiCalls=report["apiCalls"], status=report["status"])
    return report


def main() -> None:
    parser = argparse.ArgumentParser(description="원천 30·110·111 데이터로 M1 문항 적재 산출물을 생성합니다.")
    parser.add_argument("--data-root", type=Path, default=Path("data"))
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-api-generation", action="store_true")
    parser.add_argument("--essay-limit", type=int, default=20)
    parser.add_argument("--seed", type=int, default=20260810)
    parser.add_argument("--reference-final-datashape", type=Path)
    parser.add_argument("--quiet", action="store_true", help="단계별 로그를 끕니다.")
    args = parser.parse_args()
    logging.basicConfig(level=logging.WARNING if args.quiet else logging.INFO,
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s", datefmt="%H:%M:%S")
    print(json.dumps(run_build(args.data_root, args.output, allow_api_generation=args.allow_api_generation,
                                essay_limit=args.essay_limit, seed=args.seed,
                                reference_final_datashape=args.reference_final_datashape), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
