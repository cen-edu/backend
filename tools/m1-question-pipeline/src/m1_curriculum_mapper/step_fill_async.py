from __future__ import annotations

import asyncio
import copy
import json
import logging
import os
import shutil
from pathlib import Path
from typing import Any, Awaitable, Callable

from .step_fill import normalize_blank_answer, normalize_problem_data, validate_problem_data

try:
    from tqdm import tqdm
except ImportError:  # pragma: no cover - keeps the pipeline usable before reinstall
    class tqdm:  # type: ignore[no-redef]
        def __init__(self, iterable=None, **_: Any): self.total = 0
        def update(self, _: int = 1) -> None: pass
        def set_postfix(self, **_: Any) -> None: pass
        def close(self) -> None: pass


MODEL = "gpt-5-mini-2025-08-07"
logger = logging.getLogger(__name__)
AsyncRequest = Callable[[dict[str, Any], str], Awaitable[dict[str, Any]]]
CATEGORY_TITLES = {"INTERPRET": "조건과 개념 이해하기", "MODEL": "풀이 식과 전략 세우기",
                   "EXECUTE": "계산과 변형 수행하기", "ANSWER": "답 확인하기"}


def instruction() -> str:
    return """Create a Korean STEP_FILL problem from the supplied source record and return JSON only.
Return problemData, solutionText, and finalAnswer. problemData.stages must contain 1-4 genuinely necessary stages.
Each stage has category INTERPRET, MODEL, EXECUTE, or ANSWER, contentParts, and 1-2 matching blankSlots.
contentParts may contain only TEXT {type,value}, BLANK {type,blankId}, and ANSWER_REF {type,blankId}.
Every blank must be grammatically connected to an explicit cue that tells the learner what to enter.
Good: TEXT '따라서 x=' + BLANK(answer '3') + TEXT '이다.'
Good: TEXT '호의 길이는 ' + BLANK(answer '7') + TEXT ' cm이다.'
Bad: TEXT '몫과 같다.' + BLANK. Bad: ANSWER_REF + BLANK. Bad: BLANK + BLANK.
Never place a blank after a completed sentence without an answer cue. Never place adjacent blanks without explanatory TEXT.
Each blank has exactly one role. If substitution and final calculation are both tested, label them in separate clauses or stages.
Before returning JSON, perform a blank-context checklist for every blank:
1. The TEXT immediately before the BLANK must name the target or operation whose value is entered (for example 'x = ', '반지름은 ', '구하는 중심각은 ', '계산하면 ').
2. The TEXT immediately after the BLANK must complete the sentence or show a unit/punctuation boundary.
3. Never emit a bare numeric expression such as '360:90 = ' followed by a blank unless the surrounding TEXT explicitly says what the blank represents.
4. Never leave two BLANKs adjacent, and never use a blank whose only context is a completed sentence or an unexplained equation fragment.
Store atomic answers only: use '7', not '7cm'; '3', not 'x=3'; '1/2', not '정답은 1/2'.
Put units and assignment prefixes such as x=, a=, length, count, or probability in visible TEXT around the blank. The answer field must contain only the value entered by the learner.
Keep a genuine intermediate equation such as '3x=40' only when the equation itself is what the blank asks for.
blankSlots contain blankId, answer {raw,normalized}, answerType, diagnostic category, and sourceSpanIds. Every blankId must appear exactly once in contentParts and exactly once in blankSlots.
answerType is NUMERIC, SYMBOLIC_EQUIVALENCE, or TEXT_SET. Use only supplied sourceSpanIds.
ANSWER_REF may reference only an earlier-stage blank. Do not expose the final answer in visible TEXT.
For SOURCE_GROUNDED input, use its model answer and final answer as evidence. For MODEL_SOLVED input, solve the problem carefully.
The finalAnswer object also stores only the atomic value without a unit or variable-assignment prefix.
If these constraints cannot be satisfied from the supplied evidence, return a minimal safe structure only when all blanks remain explicit and atomic; otherwise do not invent a blank or explanatory answer."""


async def _openai_request(payload: dict[str, Any], model: str) -> dict[str, Any]:
    if not os.environ.get("OPENAI_API_KEY"):
        raise ValueError("OPENAI_API_KEY is required")
    from openai import AsyncOpenAI
    client = AsyncOpenAI(timeout=90, max_retries=2)
    response = await client.responses.create(
        model=model, reasoning={"effort": "low"},
        input=[{"role": "developer", "content": instruction()},
               {"role": "user", "content": json.dumps(payload, ensure_ascii=False)}],
        text={"format": {"type": "json_object"}}, max_output_tokens=3500,
    )
    if not response.output_text:
        raise ValueError("empty model output")
    value = json.loads(response.output_text)
    if not isinstance(value, dict):
        raise ValueError("model output must be an object")
    return value


def _render(parts: list[dict]) -> str:
    output = []
    for part in parts:
        if part.get("type") == "TEXT": output.append(str(part.get("value") or ""))
        elif part.get("type") in {"BLANK", "ANSWER_REF"}: output.append("{{" + str(part.get("blankId")) + "}}")
    return "".join(output)


def _materialize(base: dict, generated: dict, problem: dict) -> dict:
    units = []
    public_stages = []
    for order, stage in enumerate(problem.get("stages") or [], 1):
        category = stage["category"]
        for slot in stage.get("blankSlots") or []:
            units.append({"unitId": slot["blankId"], "unitType": "BLANK",
                          "accepted": [slot["answer"]], "answerType": slot["answerType"],
                          "diagnosticType": category, "label": None, "selectionMode": None})
        parts = stage.get("contentParts") or []
        public_stages.append({"stageId": stage["stageId"], "order": order,
                              "title": stage.get("title") or CATEGORY_TITLES[category],
                              "contentParts": parts, "textTemplate": _render(parts)})
    evidence = base.get("stepFillEvidence") or {}
    generated_final = generated.get("finalAnswer")
    if isinstance(generated_final, str):
        generated_final = {"raw": generated_final, "normalized": generated_final}
    if not isinstance(generated_final, dict):
        generated_final = {"raw": evidence.get("finalAnswer"), "normalized": evidence.get("finalAnswer")}
    final_answer = normalize_blank_answer(generated_final)
    metadata = {**(base.get("generationMetadata") or {}), "contractVersion": "step-fill-v4",
                "generationMethod": "LLM_STRUCTURED_ASYNC", "sourceQuestionRef": base["sourceRef"],
                "stepFillEvidenceMode": (base.get("stepFillEvidence") or {}).get("mode")}
    output = copy.deepcopy(base)
    output.pop("stepFillEvidence", None); output.pop("stepFillSelectionReason", None)
    output.update({"sourceType": "GENERATED", "questionTypeCode": "STEP_FILL",
                   "problemData": {"stages": public_stages},
                   "answerSpec": {"finalAnswer": [final_answer], "solutionText": str(generated.get("solutionText") or evidence.get("modelAnswerText") or ""), "units": units},
                   "generationMetadata": metadata, "pipelineStage": "STEP_FILL_ACCEPTED"})
    return output


def _append(path: Path, row: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(row, ensure_ascii=False) + "\n")


def _read(path: Path) -> list[dict]:
    if not path.exists(): return []
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


def _write_jsonl(path: Path, rows: list[dict]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(row, ensure_ascii=False) + "\n" for row in rows), encoding="utf-8")


def _generation_counts(candidates: list[dict], completed: set[str] | None = None) -> dict[str, int]:
    cache_hits = sum(1 for candidate in candidates if candidate.get("sourceRef") in (completed or set()))
    return {"cacheHits": cache_hits, "deterministicAccepted": 0,
            "generationCandidates": len(candidates), "apiCalls": 0,
            "estimatedNewCalls": len(candidates) - cache_hits}


def write_generation_candidates(candidates: list[dict], output_dir: Path) -> dict[str, int]:
    output_dir = Path(output_dir)
    _write_jsonl(output_dir / "step_fill_candidates.jsonl", candidates)
    return _generation_counts(candidates)


def _write_generation_summary(output_dir: Path, report: dict[str, Any]) -> None:
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    (Path(output_dir) / "generation_summary.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )


def execute_generation(candidates: list[dict], output_dir: Path, *, allow_api_generation: bool = False,
                       request: AsyncRequest | None = None, model: str = MODEL, concurrency: int = 4,
                       resume: bool = True) -> dict[str, Any]:
    """Persist the generation queue and call the API only after explicit approval."""
    logger.info("생성 큐 저장 시작: output=%s, candidates=%d", output_dir, len(candidates))
    candidate_report = write_generation_candidates(candidates, output_dir)
    checkpoint = Path(output_dir) / ".step_fill_work" / "checkpoint.jsonl"
    completed = {row.get("sourceRef") for row in _read(checkpoint) if row.get("status") == "ACCEPTED"} if resume else set()
    preview = _generation_counts(candidates, completed)
    logger.info("생성 큐 준비 완료: total=%d, cacheHits=%d, estimatedNewCalls=%d, api=%s",
                preview["generationCandidates"], preview["cacheHits"], preview["estimatedNewCalls"], allow_api_generation)
    if not allow_api_generation:
        _write_generation_summary(output_dir, preview)
        return preview

    print(json.dumps({"generationCandidates": preview["generationCandidates"],
                      "estimatedNewCalls": preview["estimatedNewCalls"]}, ensure_ascii=False), flush=True)
    logger.info("TQDM API 진행 시작: total=%d, concurrency=%d", len(candidates), concurrency)
    report = asyncio.run(run_async_batch(candidates, output_dir, request=request, model=model,
                                         concurrency=concurrency, resume=resume, allow_api_generation=True))
    report = {**candidate_report, **report}
    _write_generation_summary(output_dir, report)
    return report


async def run_async_batch(candidates: list[dict], output_dir: Path, *, request: AsyncRequest | None = None,
                          model: str = MODEL, concurrency: int = 4, resume: bool = True,
                          allow_api_generation: bool = False) -> dict[str, Any]:
    if concurrency < 1: raise ValueError("concurrency must be at least 1")
    if not allow_api_generation:
        return {"selected": len(candidates), "accepted": 0, "rejected": 0,
                "model": model, "concurrency": concurrency, **_generation_counts(candidates)}
    output_dir = Path(output_dir); output_dir.mkdir(parents=True, exist_ok=True)
    work = output_dir / ".step_fill_work"; checkpoint = work / "checkpoint.jsonl"
    if not resume and work.exists(): shutil.rmtree(work)
    work.mkdir(parents=True, exist_ok=True)
    checkpoint.touch(exist_ok=True)
    completed = {row.get("sourceRef") for row in _read(checkpoint) if row.get("status") == "ACCEPTED"} if resume else set()
    counts = _generation_counts(candidates, completed)
    semaphore = asyncio.Semaphore(concurrency)
    call = request or _openai_request
    write_lock = asyncio.Lock()
    progress = tqdm(total=len(candidates), desc="STEP_FILL API", unit="문항", dynamic_ncols=True)
    progress_counts = {"accepted": 0, "rejected": 0}

    async def process(base: dict):
        source_ref = base.get("sourceRef")
        if source_ref in completed: return
        async with semaphore:
            try:
                generated = await call(base, model)
                problem = normalize_problem_data(generated.get("problemData") or {})
                spans = {x.get("spanId") for x in (base.get("stepFillEvidence") or {}).get("sourceSpans") or [] if isinstance(x, dict)}
                issues = validate_problem_data(problem, known_span_ids=spans)
                evidence = base.get("stepFillEvidence") or {}
                if not (generated.get("solutionText") or evidence.get("modelAnswerText")) or not (isinstance(generated.get("finalAnswer"), dict) or evidence.get("finalAnswer")):
                    issues.append({"code": "FINAL_SOLUTION_REQUIRED", "message": "solutionText와 finalAnswer가 필요합니다.", "path": "output"})
                row = ({"sourceRef": source_ref, "status": "ACCEPTED", "record": _materialize(base, generated, problem)}
                       if not issues else {"sourceRef": source_ref, "status": "REJECTED", "issues": issues, "generated": generated})
            except Exception as error:
                row = {"sourceRef": source_ref, "status": "REJECTED",
                       "issues": [{"code": type(error).__name__, "message": str(error), "path": "request"}]}
            async with write_lock:
                _append(checkpoint, row)
                progress_counts["accepted" if row.get("status") == "ACCEPTED" else "rejected"] += 1
                progress.update(1)
                progress.set_postfix(**progress_counts)

    try:
        await asyncio.gather(*(process(row) for row in candidates))
    finally:
        progress.close()
        logger.info("TQDM API 진행 종료: total=%d", len(candidates))
    terminal = _read(checkpoint)
    by_ref = {row.get("sourceRef"): row for row in terminal}
    accepted = [row["record"] for row in by_ref.values() if row.get("status") == "ACCEPTED"]
    rejected = [{"sourceRef": row.get("sourceRef"), "status": "REJECTED", "issues": row.get("issues") or []}
                for row in by_ref.values() if row.get("status") != "ACCEPTED"]
    accepted.sort(key=lambda row: row["sourceRef"]); rejected.sort(key=lambda row: str(row.get("sourceRef")))
    for name, rows in (("step_fill_accepted.jsonl", accepted), ("step_fill_rejected.jsonl", rejected)):
        _write_jsonl(output_dir / name, rows)
    shutil.rmtree(work)
    logger.info("API 생성 결과 정리 완료: accepted=%d, rejected=%d", len(accepted), len(rejected))
    return {"selected": len(candidates), "accepted": len(accepted), "rejected": len(rejected),
            "model": model, "concurrency": concurrency,
            **{**counts, "apiCalls": counts["estimatedNewCalls"]}}
