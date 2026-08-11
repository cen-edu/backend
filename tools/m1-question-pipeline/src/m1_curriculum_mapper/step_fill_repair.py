from __future__ import annotations

import copy
import re
from typing import Any

from .step_fill import (
    UNIT_TOKEN,
    blank_has_context,
    is_atomic_answer,
    is_explanatory_answer,
    normalize_blank_answer,
)


CACHE_OVERLAY_FIELDS = ("questionTypeCode", "problemData", "answerSpec", "generationMetadata")
ASSIGNMENT = re.compile(
    r"(?P<label>(?:[∠△□]\s*)?(?:\\?[A-Za-z]+|[A-Za-z가-힣]))\s*=\s*(?P<value>.+)"
)
UNIT_SUFFIX_CAPTURE = re.compile(rf"\s*(?P<unit>{UNIT_TOKEN})\s*$", re.I)
BARE_ATOMIC_VALUE = (
    r"(?:[+-]?\d+(?:\.\d+)?(?:\s*(?:\\?pi|π))?|"
    r"[+-]?\\frac\s*\{[^{}]+\}\s*\{[^{}]+\}|"
    r"[+-]?\d+\s*/\s*\d+|[A-Za-z](?:\s*[+-]\s*\d+)?)"
)
ATOMIC_VALUE = rf"(?:{BARE_ATOMIC_VALUE}|\(\s*{BARE_ATOMIC_VALUE}\s*\))"
EQUATION = re.compile(
    rf"(?P<label>(?:[∠△□]\s*)?(?:\\?[A-Za-z]+|[A-Za-z0-9가-힣]+(?:\s*[+\-*/]\s*[A-Za-z0-9가-힣]+)*))"
    rf"\s*=\s*(?P<value>{ATOMIC_VALUE})(?P<unit>\s*{UNIT_TOKEN})?",
    re.I,
)
NAMED_VALUE = re.compile(
    rf"(?P<label>답|값|몫|개수|결과|수|길이|넓이|부피|확률|각|농도|속력|시간)"
    rf"(?P<particle>은|는|이|가)\s*(?P<value>{ATOMIC_VALUE})(?P<unit>\s*{UNIT_TOKEN})?",
    re.I,
)
COMPLETED_TEXT = re.compile(r"[.!?。]\s*$")
SAFE_CLAUSE_PREFIX = re.compile(r"(?:^|[.!?。;,]|이므로|이어서|이고|이며|되므로|따라서)\s*$")
SAFE_CLAUSE_SUFFIX = re.compile(
    r"^\s*(?:(?:이다|였다|이므로|이어서|이고|이며|된다|되므로)"
    r"(?=\s|[.!?。;,]|$)|[.!?。;,]|$)"
)
PARENTHESIZED_ATOMIC = re.compile(rf"^\(\s*(?P<value>{BARE_ATOMIC_VALUE})\s*\)$")


def _issue(code: str, message: str, path: str) -> dict[str, str]:
    return {"code": code, "message": message, "path": path}


def _surface(value: Any) -> str:
    text = str(value or "").strip().replace("$", "").replace("\\left", "").replace("\\right", "")
    return re.sub(r"\\(?:mathrm|text)\s*\{([^{}]*)\}", r"\1", text).strip()


def _render(parts: list[dict]) -> str:
    rendered = []
    for part in parts:
        if part.get("type") == "TEXT":
            rendered.append(str(part.get("value") or ""))
        elif part.get("type") in {"BLANK", "ANSWER_REF"}:
            rendered.append("{{" + str(part.get("blankId") or "") + "}}")
    return "".join(rendered)


def _answer_type(answer: dict[str, str]) -> str:
    value = answer.get("normalized", "")
    return "NUMERIC" if re.fullmatch(r"[+-]?(?:\d+(?:\.\d+)?|\d+/\d+)", value) else "SYMBOLIC_EQUIVALENCE"


def _is_complete_clause_match(text: str, match: re.Match) -> bool:
    if not SAFE_CLAUSE_PREFIX.search(text[:match.start()]):
        return False
    return bool(SAFE_CLAUSE_SUFFIX.match(text[match.end():]))


def _canonical_atomic_value(value: str) -> str:
    text = value.strip()
    parenthesized = PARENTHESIZED_ATOMIC.fullmatch(text)
    return parenthesized.group("value").strip() if parenthesized else text


def _solution_clauses(solution: str) -> list[dict]:
    text = _surface(solution).replace("\\therefore", " 따라서 ").replace("∴", " 따라서 ")
    clauses = []
    occupied = []
    for pattern in (EQUATION, NAMED_VALUE):
        for match in pattern.finditer(text):
            if any(start <= match.start() < end for start, end in occupied):
                continue
            if not _is_complete_clause_match(text, match):
                continue
            answer = normalize_blank_answer({"raw": _canonical_atomic_value(match.group("value"))})
            if not answer["raw"] or is_explanatory_answer(answer["raw"]):
                continue
            label = str(match.group("label") or "").strip()
            if pattern is NAMED_VALUE:
                label += str(match.group("particle") or "")
            clauses.append({
                "label": label, "answer": answer["raw"],
                "unit": str(match.groupdict().get("unit") or "").strip(), "offset": match.start(),
            })
            occupied.append(match.span())
    clauses.sort(key=lambda clause: clause["offset"])
    return [clause for index, clause in enumerate(clauses)
            if not index or (clause["label"], clause["answer"]) != (clauses[index - 1]["label"], clauses[index - 1]["answer"])]


def compile_110_step_fill(record: dict) -> tuple[dict | None, list[dict]]:
    source_ref = str(record.get("sourceRef") or "")
    evidence = (record.get("sourceMetadata") or {}).get("sourceEvidence") or {}
    solution = str(evidence.get("modelAnswerText") or "").strip()
    final = normalize_blank_answer({"raw": evidence.get("finalAnswer")})
    clauses = _solution_clauses(solution)

    def unsafe(message: str, path: str) -> tuple[None, list[dict]]:
        return None, [_issue("UNSAFE_STEP_FILL_STRUCTURE", message, path)]

    if not source_ref.startswith("110:") or not solution or not final["raw"] or not clauses:
        return unsafe("명확한 대상과 단일 정답을 갖는 풀이 단계를 구성할 수 없습니다.", "sourceMetadata.sourceEvidence")
    final_matches = [index for index, clause in enumerate(clauses)
                     if normalize_blank_answer({"raw": clause["answer"]})["normalized"] == final["normalized"]]
    if not final_matches:
        return unsafe("풀이에서 최종 정답과 일치하는 단계를 찾을 수 없습니다.", "sourceMetadata.sourceEvidence.finalAnswer")
    final_index = final_matches[-1]
    selected = [clause for index, clause in enumerate(clauses) if index != final_index][-3:] + [clauses[final_index]]

    stages, units = [], []
    for index, clause in enumerate(selected, start=1):
        blank_id = f"B{index}"
        is_final = clause is clauses[final_index]
        prefix = f"{clause['label']}="
        if is_final:
            prefix = f"따라서 {prefix}"
        suffix = (f" {clause['unit']}" if clause["unit"] else "") + ("이다." if is_final else "이므로")
        parts = [{"type": "TEXT", "value": prefix}, {"type": "BLANK", "blankId": blank_id}, {"type": "TEXT", "value": suffix}]
        stages.append({
            "stageId": f"S{index}", "order": index,
            "title": "답 확인하기" if is_final else "계산과 변형 수행하기",
            "contentParts": parts, "textTemplate": _render(parts),
        })
        answer = normalize_blank_answer({"raw": clause["answer"]})
        units.append({
            "unitId": blank_id, "unitType": "BLANK", "accepted": [answer], "answerType": _answer_type(answer),
            "diagnosticType": "ANSWER" if is_final else "EXECUTE", "label": None, "selectionMode": None,
        })

    output = copy.deepcopy(record)
    output.update({
        "sourceType": "GENERATED", "questionTypeCode": "STEP_FILL", "problemData": {"stages": stages},
        "answerSpec": {"finalAnswer": [final], "solutionText": solution, "units": units},
        "generationMetadata": {
            **(record.get("generationMetadata") or {}), "contractVersion": "step-fill-v4",
            "generationMethod": "DETERMINISTIC_SOURCE_COMPILE", "sourceQuestionRef": source_ref,
            "stepFillEvidenceMode": "SOURCE_GROUNDED",
        },
        "pipelineStage": "STEP_FILL_ACCEPTED",
    })
    return output, []


def _insert_assignment_label(parts: list[dict], blank_index: int, label: str) -> bool:
    previous = parts[blank_index - 1] if blank_index else None
    if not isinstance(previous, dict) or previous.get("type") != "TEXT":
        return False
    visible = str(previous.get("value") or "")
    if re.search(rf"{re.escape(label)}\s*=\s*$", visible):
        return True
    if COMPLETED_TEXT.search(visible) or re.search(r"=\s*$", visible):
        return False
    previous["value"] = visible.rstrip() + (" " if visible and not visible.endswith(" ") else "") + f"{label}="
    return True


def _insert_unit(parts: list[dict], blank_index: int, unit: str) -> bool:
    following = parts[blank_index + 1] if blank_index + 1 < len(parts) else None
    if not isinstance(following, dict) or following.get("type") != "TEXT":
        return False
    visible = str(following.get("value") or "")
    if re.match(rf"^\s*{re.escape(unit)}(?:\s|(?:이|였|의|를|을|에)|[.,;:)])", visible, re.I):
        return True
    if not re.match(r"^\s*(?:이다|였다|이며|이므로|이고|[.,;:)])", visible):
        return False
    following["value"] = f" {unit}" + visible
    return True


def _repair_answer(parts: list[dict], blank_index: int, answer: Any, path: str) -> tuple[dict | None, list[dict]]:
    item = answer if isinstance(answer, dict) else {"raw": answer}
    raw = _surface(item.get("raw"))
    if not raw:
        return None, [_issue("BLANK_ANSWER_REQUIRED", "빈칸 정답이 필요합니다.", path)]
    if is_explanatory_answer(raw):
        return None, [_issue("EXPLANATORY_BLANK_ANSWER", "빈칸 정답에는 풀이 문장을 저장할 수 없습니다.", path)]
    assignment = ASSIGNMENT.fullmatch(raw)
    stripped = raw
    if assignment:
        stripped = assignment.group("value").strip()
        if not _insert_assignment_label(parts, blank_index, assignment.group("label").strip()):
            return None, [_issue("UNSAFE_ASSIGNMENT_REPAIR", "제거한 대상 라벨을 인접 텍스트에 표시할 수 없습니다.", path)]
    unit_match = UNIT_SUFFIX_CAPTURE.search(stripped)
    if unit_match:
        unit = unit_match.group("unit")
        stripped = stripped[:unit_match.start()].strip()
        if not _insert_unit(parts, blank_index, unit):
            return None, [_issue("UNSAFE_UNIT_REPAIR", "제거한 단위를 인접 텍스트에 표시할 수 없습니다.", path)]
    if not stripped or not is_atomic_answer(stripped):
        return None, [_issue("NON_ATOMIC_BLANK_ANSWER", "빈칸 정답은 단일 값이나 식이어야 합니다.", path)]
    normalized = normalize_blank_answer({**item, "raw": stripped, "normalized": stripped})
    return {**item, **normalized}, []


def repair_step_fill_record(record: dict) -> tuple[dict | None, list[dict]]:
    output = copy.deepcopy(record)
    if output.get("questionTypeCode") != "STEP_FILL":
        return None, [_issue("STEP_FILL_REQUIRED", "STEP_FILL 캐시 문항이 필요합니다.", "questionTypeCode")]
    stages = (output.get("problemData") or {}).get("stages")
    units = (output.get("answerSpec") or {}).get("units")
    if not isinstance(stages, list) or not 1 <= len(stages) <= 4:
        return None, [_issue("INVALID_STAGE_COUNT", "stages는 1~4개여야 합니다.", "problemData.stages")]
    if not isinstance(units, list):
        return None, [_issue("STEP_FILL_UNIT_MISMATCH", "빈칸과 정답 unit을 대응시킬 수 없습니다.", "answerSpec.units")]

    locations, old_ids = [], []
    for stage_index, stage in enumerate(stages):
        if not isinstance(stage, dict) or not isinstance(stage.get("contentParts"), list):
            return None, [_issue("INVALID_CONTENT_PARTS", "contentParts 배열이 필요합니다.", f"problemData.stages[{stage_index}].contentParts")]
        stage["stageId"], stage["order"] = f"S{stage_index + 1}", stage_index + 1
        for part_index, part in enumerate(stage["contentParts"]):
            if isinstance(part, dict) and part.get("type") == "BLANK":
                locations.append((stage_index, part_index)); old_ids.append(part.get("blankId"))
    if not locations or len(locations) != len(units):
        return None, [_issue("STEP_FILL_UNIT_MISMATCH", "빈칸 수와 정답 unit 수가 일치하지 않습니다.", "answerSpec.units")]

    unit_by_id = {unit.get("unitId"): unit for unit in units if isinstance(unit, dict) and unit.get("unitId")}
    ordered_units = ([unit_by_id[blank_id] for blank_id in old_ids]
                     if len(unit_by_id) == len(units) and all(blank_id in unit_by_id for blank_id in old_ids)
                     else units)
    if not all(isinstance(unit, dict) for unit in ordered_units):
        return None, [_issue("STEP_FILL_UNIT_MISMATCH", "빈칸과 정답 unit을 대응시킬 수 없습니다.", "answerSpec.units")]

    id_map, repaired_units, issues = {}, [], []
    for index, ((stage_index, part_index), old_id, unit) in enumerate(zip(locations, old_ids, ordered_units), start=1):
        blank_id = f"B{index}"
        if old_id:
            id_map[old_id] = blank_id
        parts = stages[stage_index]["contentParts"]
        parts[part_index]["blankId"] = blank_id
        accepted = unit.get("accepted")
        if not isinstance(accepted, list) or not accepted:
            issues.append(_issue("BLANK_ANSWER_REQUIRED", "빈칸 정답이 필요합니다.", f"answerSpec.units[{index - 1}].accepted"))
            continue
        repaired_answers = []
        for answer_index, answer in enumerate(accepted):
            repaired, answer_issues = _repair_answer(parts, part_index, answer, f"answerSpec.units[{index - 1}].accepted[{answer_index}]")
            issues.extend(answer_issues)
            if repaired is not None:
                repaired_answers.append(repaired)
        repaired_unit = copy.deepcopy(unit)
        repaired_unit.update({"unitId": blank_id, "unitType": repaired_unit.get("unitType") or "BLANK", "accepted": repaired_answers})
        if repaired_answers:
            repaired_unit["answerType"] = repaired_unit.get("answerType") or _answer_type(repaired_answers[0])
        repaired_units.append(repaired_unit)

    for stage_index, stage in enumerate(stages):
        for part_index, part in enumerate(stage["contentParts"]):
            if not isinstance(part, dict) or part.get("type") not in {"TEXT", "BLANK", "ANSWER_REF"}:
                issues.append(_issue("INVALID_CONTENT_PART", "지원하지 않는 content part입니다.", f"problemData.stages[{stage_index}].contentParts[{part_index}]"))
                continue
            if isinstance(part, dict) and part.get("type") == "ANSWER_REF":
                if part.get("blankId") not in id_map:
                    issues.append(_issue("INVALID_ANSWER_REF", "알 수 없는 빈칸을 참조합니다.", f"problemData.stages[{stage_index}].contentParts[{part_index}]"))
                else:
                    part["blankId"] = id_map[part["blankId"]]
            if part.get("type") == "BLANK" and part_index:
                previous = stage["contentParts"][part_index - 1]
                if isinstance(previous, dict) and previous.get("type") in {"BLANK", "ANSWER_REF"}:
                    issues.append(_issue("ADJACENT_BLANKS_WITHOUT_LABEL", "연속 빈칸 사이에 역할 설명이 필요합니다.", f"problemData.stages[{stage_index}].contentParts[{part_index}]"))
        for part_index, part in enumerate(stage["contentParts"]):
            if isinstance(part, dict) and part.get("type") == "BLANK" and not blank_has_context(stage["contentParts"], part_index):
                issues.append(_issue("AMBIGUOUS_BLANK_CONTEXT", "빈칸 주변에 입력할 값이나 식의 역할이 표시되어야 합니다.", f"problemData.stages[{stage_index}].contentParts[{part_index}]"))
        stage["textTemplate"] = _render(stage["contentParts"])
    if issues:
        return None, issues
    output["answerSpec"]["units"] = repaired_units
    final_answers = output["answerSpec"].get("finalAnswer") or []
    if any(is_explanatory_answer((answer or {}).get("raw") if isinstance(answer, dict) else answer)
           for answer in final_answers):
        return None, [_issue("EXPLANATORY_FINAL_ANSWER", "최종 정답에는 풀이 문장 대신 단일 값을 저장합니다.", "answerSpec.finalAnswer")]
    normalized_final_answers = [
        {**(answer if isinstance(answer, dict) else {"raw": answer}),
         **normalize_blank_answer(answer if isinstance(answer, dict) else {"raw": answer})}
        for answer in final_answers
    ]
    if any(not is_atomic_answer(answer.get("raw")) for answer in normalized_final_answers):
        return None, [_issue("NON_ATOMIC_FINAL_ANSWER", "최종 정답은 단일 값이나 식이어야 합니다.", "answerSpec.finalAnswer")]
    output["answerSpec"]["finalAnswer"] = normalized_final_answers
    return output, []


def resolve_step_fill_cache(base_rows: list[dict], cached_rows: list[dict]) -> tuple[list[dict], list[dict], list[dict]]:
    caches_by_ref: dict[str, list[dict]] = {}
    for cached in cached_rows:
        source_ref = cached.get("sourceRef") if isinstance(cached, dict) else None
        if source_ref:
            caches_by_ref.setdefault(source_ref, []).append(cached)
    base_refs = {base.get("sourceRef") for base in base_rows if isinstance(base, dict)}
    accepted, candidates, rejected = [], [], []
    for base in base_rows:
        source_ref = str(base.get("sourceRef") or "")
        if not source_ref.startswith("110:"):
            accepted.append(copy.deepcopy(base)); continue
        repaired_cache = None
        for cached in caches_by_ref.get(source_ref, []):
            repaired, _ = repair_step_fill_record(cached)
            if repaired is not None:
                repaired_cache = repaired; break
        if repaired_cache is not None:
            output = copy.deepcopy(base)
            for field in CACHE_OVERLAY_FIELDS:
                if field in repaired_cache:
                    output[field] = copy.deepcopy(repaired_cache[field])
            accepted.append(output); continue
        compiled, _ = compile_110_step_fill(base)
        if compiled is not None:
            accepted.append(compiled)
        else:
            candidates.append(copy.deepcopy(base))
    for source_ref in sorted(set(caches_by_ref) - base_refs):
        rejected.append({
            "sourceRef": source_ref, "status": "REJECTED",
            "issues": [_issue("BASE_RECORD_MISSING", "새로 정규화된 기반 문항을 찾을 수 없습니다.", "sourceRef")],
        })
    return accepted, candidates, sorted(rejected, key=lambda row: str(row.get("sourceRef")))
