from __future__ import annotations

import copy
import re
from typing import Any


ALLOWED_PART_TYPES = {"TEXT", "BLANK", "ANSWER_REF"}
ALLOWED_CATEGORIES = {"INTERPRET", "MODEL", "EXECUTE", "ANSWER"}
ALLOWED_ANSWER_TYPES = {"NUMERIC", "SYMBOLIC_EQUIVALENCE", "TEXT_SET", "OPTION_SET", "QUANTITY"}
UNIT_SUFFIX = re.compile(r"\s*(?:cm|mm|km|m|kg|g|L|ml|도|°|%|원|개|명|시간|분|초|권|번|회|쌍|배|점|마리)\s*$", re.I)
ATOMIC_ASSIGNMENT = re.compile(r"^\s*[A-Za-z가-힣]\s*=\s*(.+)$")
FRAC = re.compile(r"\\frac\s*\{([^{}]+)\}\s*\{([^{}]+)\}")
TARGET_CUE = re.compile(
    r"(?:=|값(?:은|이|는)?|답(?:은|이|는)?|몫은|개수는|결과는|"
    r"수는|길이는|넓이는|부피는|확률은|각은|농도는|속력은|시간은)\s*$"
)
UNIT_TOKEN = r"(?:cm|mm|km|m|kg|g|L|ml|도|°|%|원|개|명|시간|분|초|권|번|회|쌍|배|점|마리)"
RIGHT_CONTEXT_CUE = re.compile(
    rf"^(?:\s*{UNIT_TOKEN}(?=\s*(?:$|[.,;:)]|이다|였다|되다|이며|이므로|이고|을|를|의|에|은|는|이|가))"
    rf"|\s*(?:이다|였다|되다|이며|이므로|이고|=))"
)
EXPLANATORY_PREDICATE = re.compile(
    r"(?:이다|였다|한다|된다|합니다|입니다|필요하다|구한다|알 수 있다)[.!?]?$"
)
ATOMIC_KOREAN = re.compile(r"^[가-힣]+$")
ATOMIC_MATH = re.compile(r"^[0-9A-Za-zπ+*/=<>^().{}\[\]\\_|:%~°√-]+$")


def _issue(code: str, message: str, path: str) -> dict[str, str]:
    return {"code": code, "message": message, "path": path}


def _surface(value: Any) -> str:
    text = str(value or "").strip().replace("$", "").replace("\\left", "").replace("\\right", "")
    text = re.sub(r"\\(?:mathrm|text)\s*\{([^{}]*)\}", r"\1", text)
    return text.strip()


def normalize_blank_answer(answer: dict[str, Any]) -> dict[str, str]:
    raw = _surface(answer.get("raw"))
    match = ATOMIC_ASSIGNMENT.fullmatch(raw)
    if match:
        raw = match.group(1).strip()
    raw = UNIT_SUFFIX.sub("", raw).strip()
    normalized = _surface(answer.get("normalized") or raw)
    match = ATOMIC_ASSIGNMENT.fullmatch(normalized)
    if match:
        normalized = match.group(1).strip()
    normalized = UNIT_SUFFIX.sub("", normalized).strip()
    normalized = FRAC.sub(lambda m: f"{m.group(1)}/{m.group(2)}", normalized)
    normalized = re.sub(r"\s+", "", normalized)
    return {"raw": raw, "normalized": normalized}


def is_explanatory_answer(value: Any) -> bool:
    """Return whether a blank answer is prose rather than an atomic value/expression."""
    text = _surface(value)
    if not text:
        return False
    if re.match(r"^(?:따라서|그러므로|계산하면|정답은|답은)\s+", text):
        return True
    return bool(EXPLANATORY_PREDICATE.search(text) and re.search(r"[가-힣]", text))


def is_atomic_answer(value: Any) -> bool:
    """Accept one atomic text token or a compact mathematical value/expression."""
    text = _surface(value)
    if not text or is_explanatory_answer(text):
        return False
    if ATOMIC_KOREAN.fullmatch(text):
        return True
    if re.search(r"[가-힣]", text):
        return False
    compact = re.sub(r"\s+", "", text)
    return bool(re.search(r"[0-9A-Za-zπ√]", compact) and ATOMIC_MATH.fullmatch(compact))


def blank_has_context(parts: list[Any], index: int) -> bool:
    """Check that at least one adjacent visible text part identifies the blank's role."""
    previous = parts[index - 1] if index else None
    following = parts[index + 1] if index + 1 < len(parts) else None
    prior_text = previous.get("value", "") if isinstance(previous, dict) and previous.get("type") == "TEXT" else ""
    next_text = following.get("value", "") if isinstance(following, dict) and following.get("type") == "TEXT" else ""
    if re.search(r"[.!?。]\s*$", str(prior_text)) and not TARGET_CUE.search(str(prior_text)):
        return False
    return bool(TARGET_CUE.search(str(prior_text)) or RIGHT_CONTEXT_CUE.search(str(next_text)))


def normalize_problem_data(problem_data: dict[str, Any]) -> dict[str, Any]:
    output = copy.deepcopy(problem_data)
    stages = [stage for stage in output.get("stages") or [] if isinstance(stage, dict)
              and any(isinstance(part, dict) and part.get("type") == "BLANK" for part in stage.get("contentParts") or [])
              and isinstance(stage.get("blankSlots"), list) and stage.get("blankSlots")]
    output["stages"] = stages
    for stage_index, stage in enumerate(stages, start=1):
        if not isinstance(stage, dict):
            continue
        stage["stageId"] = f"S{stage_index}"
        category = stage.get("category")
        for slot in stage.get("blankSlots") or []:
            if isinstance(slot, dict):
                slot["answer"] = normalize_blank_answer(slot.get("answer") or {})
                slot["diagnosticType"] = category
                if slot.get("answerType") == "QUANTITY":
                    slot["answerType"] = "NUMERIC" if re.fullmatch(r"[+-]?\d+(?:\.\d+)?", slot["answer"]["normalized"]) else "SYMBOLIC_EQUIVALENCE"
    return output


def validate_problem_data(problem_data: Any, *, known_span_ids: set[str] | None = None) -> list[dict[str, str]]:
    if not isinstance(problem_data, dict):
        return [_issue("PROBLEM_DATA_OBJECT_REQUIRED", "problemData는 객체여야 합니다.", "problemData")]
    stages = problem_data.get("stages")
    if not isinstance(stages, list) or not 1 <= len(stages) <= 4:
        return [_issue("INVALID_STAGE_COUNT", "stages는 1~4개여야 합니다.", "stages")]
    issues = []
    all_blank_ids = set()
    prior_blank_ids = set()
    for stage_index, stage in enumerate(stages):
        path = f"stages[{stage_index}]"
        if not isinstance(stage, dict):
            issues.append(_issue("INVALID_STAGE", "단계는 객체여야 합니다.", path)); continue
        category = stage.get("category")
        if category not in ALLOWED_CATEGORIES:
            issues.append(_issue("INVALID_STAGE_CATEGORY", "지원하지 않는 단계 유형입니다.", f"{path}.category"))
        parts = stage.get("contentParts")
        if not isinstance(parts, list):
            issues.append(_issue("INVALID_CONTENT_PARTS", "contentParts 배열이 필요합니다.", f"{path}.contentParts")); parts = []
        content_blank_ids = []
        for index, part in enumerate(parts):
            part_path = f"{path}.contentParts[{index}]"
            if not isinstance(part, dict) or part.get("type") not in ALLOWED_PART_TYPES:
                issues.append(_issue("INVALID_CONTENT_PART", "지원하지 않는 content part입니다.", part_path)); continue
            if part["type"] == "ANSWER_REF" and part.get("blankId") not in prior_blank_ids:
                issues.append(_issue("INVALID_ANSWER_REF", "앞 단계의 빈칸만 참조할 수 있습니다.", part_path))
            if part["type"] != "BLANK":
                continue
            blank_id = part.get("blankId")
            content_blank_ids.append(blank_id)
            previous = parts[index - 1] if index else None
            if isinstance(previous, dict) and previous.get("type") in {"BLANK", "ANSWER_REF"}:
                issues.append(_issue("ADJACENT_BLANKS_WITHOUT_LABEL", "연속 빈칸 사이에 역할 설명이 필요합니다.", part_path))
            if not blank_has_context(parts, index):
                issues.append(_issue("AMBIGUOUS_BLANK_CONTEXT", "빈칸 앞에 입력할 값이나 식의 역할을 명시해야 합니다.", part_path))
        slots = stage.get("blankSlots")
        if not isinstance(slots, list) or not 1 <= len(slots) <= 2:
            issues.append(_issue("BLANK_SLOTS_COUNT", "각 단계에는 1~2개의 빈칸이 필요합니다.", f"{path}.blankSlots")); slots = []
        slot_ids = []
        for index, slot in enumerate(slots):
            slot_path = f"{path}.blankSlots[{index}]"
            if not isinstance(slot, dict) or not isinstance(slot.get("blankId"), str):
                issues.append(_issue("INVALID_BLANK_SLOT", "blankId가 필요합니다.", slot_path)); continue
            blank_id = slot["blankId"]; slot_ids.append(blank_id)
            if blank_id in all_blank_ids:
                issues.append(_issue("DUPLICATE_BLANK_ID", "blankId는 유일해야 합니다.", slot_path))
            all_blank_ids.add(blank_id)
            answer = slot.get("answer")
            if not isinstance(answer, dict) or not answer.get("raw"):
                issues.append(_issue("BLANK_ANSWER_REQUIRED", "빈칸 정답이 필요합니다.", slot_path))
            else:
                raw = str(answer.get("raw") or "")
                if is_explanatory_answer(raw):
                    issues.append(_issue("EXPLANATORY_BLANK_ANSWER", "빈칸 정답에는 풀이 문장 대신 단일 값이나 식을 저장합니다.", f"{slot_path}.answer"))
                elif not is_atomic_answer(raw):
                    issues.append(_issue("NON_ATOMIC_BLANK_ANSWER", "빈칸 정답은 단일 값이나 식이어야 합니다.", f"{slot_path}.answer"))
                if ATOMIC_ASSIGNMENT.fullmatch(_surface(raw)):
                    issues.append(_issue("ANSWER_CONTAINS_ASSIGNMENT_PREFIX", "단순 값 정답에는 변수= 접두사를 저장하지 않습니다.", f"{slot_path}.answer"))
                if UNIT_SUFFIX.search(_surface(raw)):
                    issues.append(_issue("ANSWER_CONTAINS_UNIT", "정답에는 단위를 저장하지 않습니다.", f"{slot_path}.answer"))
            if slot.get("answerType") not in ALLOWED_ANSWER_TYPES:
                issues.append(_issue("ANSWER_TYPE_REQUIRED", "지원하는 answerType이 필요합니다.", f"{slot_path}.answerType"))
            spans = slot.get("sourceSpanIds")
            if not isinstance(spans, list) or not spans:
                issues.append(_issue("SOURCE_SPAN_REQUIRED", "근거 span이 필요합니다.", f"{slot_path}.sourceSpanIds"))
            elif known_span_ids is not None and any(span not in known_span_ids for span in spans):
                issues.append(_issue("UNKNOWN_SOURCE_SPAN", "알 수 없는 근거 span입니다.", f"{slot_path}.sourceSpanIds"))
        if sorted(str(x) for x in content_blank_ids) != sorted(slot_ids):
            issues.append(_issue("PLACEHOLDER_MISMATCH", "contentParts와 blankSlots가 일치하지 않습니다.", path))
        prior_blank_ids.update(slot_ids)
    return issues
