from __future__ import annotations

import argparse
import json
import logging
from collections import Counter
from pathlib import Path

from .raw_sources import iter_raw_json
from .step_fill_async import MODEL, execute_generation
from .step_fill_candidates import select_candidates_with_audit

logger = logging.getLogger(__name__)


def _read_jsonl(path: Path) -> list[dict]:
    if not Path(path).exists():
        return []
    return [json.loads(line) for line in Path(path).read_text(encoding="utf-8").splitlines() if line]


def _rejection_identity(row: dict) -> tuple[str, str, tuple[str, ...]]:
    issue_codes = tuple(sorted(str(issue.get("code") or "") for issue in row.get("issues") or []
                               if isinstance(issue, dict)))
    return str(row.get("sourceRef") or ""), str(row.get("status") or "REJECTED"), issue_codes


def _first_dict(value) -> dict:
    while isinstance(value, list): value = value[0] if value else {}
    return value if isinstance(value, dict) else {}


def _answer_evidence(root: Path, wanted_ids: set[str]) -> dict[str, dict]:
    logger.info("원천 답안 근거 스캔 시작: root=%s, 대상 ID=%d", root, len(wanted_ids))
    index = {}
    for source_file, raw in iter_raw_json(root):
        source_id = str(raw.get("id") or "")
        if source_id not in wanted_ids or source_id in index:
            continue
        answer = _first_dict(raw.get("answer_info"))
        if not answer:
            continue
        answer_text = str(answer.get("answer_text") or "").strip()
        final_answer = next((str(item.get("text") or "").strip() for item in answer.get("answer_bbox") or []
                             if isinstance(item, dict) and item.get("type") == "answer" and str(item.get("text") or "").strip()), "")
        if answer_text and final_answer:
            index[source_id] = {"modelAnswerText": answer_text, "finalAnswer": final_answer, "sourceFile": source_file}
    logger.info("원천 답안 근거 스캔 완료: root=%s, 근거 확보=%d/%d", root, len(index), len(wanted_ids))
    return index


def prepare_candidates(rows: list[dict], baseline_110_refs: set[str] | list[dict], source_110: Path, source_111: Path) -> tuple[list[dict], list[dict]]:
    logger.info("후보 준비 시작: 입력=%d, baseline=%d", len(rows), len(baseline_110_refs))
    selected, duplicate_audits = select_candidates_with_audit(rows, baseline_110_refs)
    logger.info("중복 제거 완료: 선택=%d, 중복 감사=%d", len(selected), len(duplicate_audits))
    allowed = {"110", "111"}
    excluded = [row for row in selected if row.get("sourceRef", "").partition(":")[0] not in allowed]
    if excluded:
        logger.warning("110·111 외 문항 제외: %d건", len(excluded))
        selected = [row for row in selected if row not in excluded]
    wanted_110 = {row["sourceRef"].partition(":")[2] for row in selected if row["sourceRef"].startswith("110:")}
    wanted_111 = {row["sourceRef"].partition(":")[2] for row in selected if row["sourceRef"].startswith("111:")}
    answers_by_dataset = {"110": _answer_evidence(source_110, wanted_110), "111": _answer_evidence(source_111, wanted_111)}
    candidates, rejected = [], []
    for row in selected:
        source_ref = row["sourceRef"]
        spans = [{"spanId": "Q-1", "text": str(row.get("promptText") or "")}]
        figure_text = " ".join(str(block.get("text") or "") for block in row.get("contentBlocks") or []
                               if isinstance(block, dict) and block.get("blockKind") == "FIGURE_TEXT")
        if figure_text: spans.append({"spanId": "Q-F1", "text": figure_text})
        dataset, _, source_id = source_ref.partition(":")
        evidence = answers_by_dataset[dataset].get(source_id)
        if not evidence:
            rejected.append({"sourceRef": source_ref, "status": "REJECTED",
                             "issues": [{"code": "SOURCE_ANSWER_MISSING", "message": f"{dataset} 모범답안 근거를 찾을 수 없습니다.", "path": "source"}]})
            continue
        spans.extend([{"spanId": "A-1", "text": evidence["modelAnswerText"]},
                      {"spanId": "F-A1", "text": evidence["finalAnswer"]}])
        mode = "SOURCE_GROUNDED"
        candidates.append({**row, "stepFillEvidence": {"mode": mode, "sourceSpans": spans,
                            "modelAnswerText": evidence["modelAnswerText"] if evidence else None,
                            "finalAnswer": evidence["finalAnswer"] if evidence else None}})
    logger.info("후보 준비 완료: API 후보=%d, 사전 거절=%d, 근거 누락=%d",
                len(candidates), len(rejected), sum(1 for row in rejected if any(
                    issue.get("code") == "SOURCE_ANSWER_MISSING" for issue in row.get("issues") or [])))
    return candidates, [*duplicate_audits, *rejected]


def selection_summary(questions_path: Path, baseline_110_path: Path, source_110: Path, source_111: Path) -> tuple[dict, list[dict], list[dict]]:
    logger.info("입력 파일 읽기: questions=%s, baseline=%s", questions_path, baseline_110_path)
    rows = _read_jsonl(questions_path)
    baseline_rows = _read_jsonl(baseline_110_path)
    logger.info("입력 파일 읽기 완료: questions=%d, baseline=%d", len(rows), len(baseline_rows))
    candidates, rejected = prepare_candidates(rows, baseline_rows, source_110, source_111)
    duplicate_audits = [row for row in rejected if any(issue.get("code") == "DUPLICATE_110_CONTENT" for issue in row.get("issues") or [])]
    summary = {"selected": len(candidates) + len(rejected) - len(duplicate_audits), "ready": len(candidates), "preflightRejected": len(rejected),
               "duplicates": len(duplicate_audits),
               "byDataset": dict(Counter(row["sourceRef"].partition(":")[0] for row in candidates)),
               "byEvidenceMode": dict(Counter(row["stepFillEvidence"]["mode"] for row in candidates))}
    return summary, candidates, rejected


def run(questions_path: Path, baseline_110_path: Path, source_110: Path, source_111: Path, output_dir: Path, *,
        model: str = MODEL, concurrency: int = 4, allow_api_generation: bool = False, request=None) -> dict:
    logger.info("STEP_FILL 실행 시작: output=%s, model=%s, concurrency=%d, api=%s",
                output_dir, model, concurrency, allow_api_generation)
    summary, candidates, preflight_rejected = selection_summary(questions_path, baseline_110_path, source_110, source_111)
    logger.info("사전 검증 요약: %s", json.dumps(summary, ensure_ascii=False, sort_keys=True))
    report = execute_generation(candidates, output_dir, allow_api_generation=allow_api_generation,
                                request=request, model=model, concurrency=concurrency)
    rejected_path = Path(output_dir) / "step_fill_rejected.jsonl"
    existing = _read_jsonl(rejected_path)
    merged_rejected = {_rejection_identity(row): row for row in [*existing, *preflight_rejected]}
    all_rejected = sorted(merged_rejected.values(), key=_rejection_identity)
    rejected_path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in all_rejected), encoding="utf-8")
    result = {**summary, **report, "rejected": len(all_rejected)}
    (Path(output_dir) / "generation_summary.json").write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="선택된 110·111 문항을 asyncio 병렬 STEP_FILL로 생성합니다")
    parser.add_argument("--questions", type=Path, required=True)
    parser.add_argument("--baseline-110", type=Path, required=True)
    parser.add_argument("--source-110", type=Path, required=True)
    parser.add_argument("--source-111", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--model", default=MODEL); parser.add_argument("--concurrency", type=int, default=4)
    parser.add_argument("--allow-api-generation", action="store_true")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s", force=True)
    logger.info("명령 수신: questions=%s, output=%s", args.questions, args.output)
    if args.dry_run:
        summary, _, _ = selection_summary(args.questions, args.baseline_110, args.source_110, args.source_111)
        print(json.dumps(summary, ensure_ascii=False, indent=2)); return
    print(json.dumps(run(args.questions, args.baseline_110, args.source_110, args.source_111, args.output,
                         model=args.model, concurrency=args.concurrency,
                         allow_api_generation=args.allow_api_generation), ensure_ascii=False, indent=2))


if __name__ == "__main__": main()
