from __future__ import annotations

import re

from .models import AnswerSlotPlan


_ASSIGNMENT = re.compile(r"^\s*([A-Za-z가-힣][A-Za-z0-9가-힣_]*)\s*([=:])\s*(.+?)\s*$")
_PREDICATE = re.compile(r"^\s*(.+?(?:은|는))\s+(.+?)(이다\.?|이고\.?)(?:\s*)$")
_EXPLANATORY_PREFIX = re.compile(r"^\s*(?:정답은|따라서)\s*")
_UNIT_SUFFIX = re.compile(r"^(.+?)(\s*(?:cm|mm|km|m|㎡|㎠|㎥|L|ml|kg|g|원|개|명|도|시간|분|초))$")
_MULTIPLE_LABELED_CLAUSES = re.compile(r".+?(?:은|는)\s*.+?이고\s*.+?(?:은|는)\s*.+?이다\s*(?:[.。]\s*)?")
_SENTENCE_MARKER = re.compile(r"정답은|따라서")
_TERMINAL_EXPLANATION = re.compile(r"(?:된다|구한다|알\s*수\s*있다)\.?$")


def atomic_answer(value: str) -> tuple[str, str, str]:
    raw = value.strip()
    normalized = re.sub(r"\s*,\s*", ",", raw)
    normalized = re.sub(r"\s+", " ", normalized)
    if re.fullmatch(r"[-+]?\d+(?:\.\d+)?(?:/\d+(?:\.\d+)?)?", normalized):
        value_kind = "NUMBER"
    elif "=" in normalized:
        value_kind = "EQUATION"
    else:
        value_kind = "TEXT"
    return raw, normalized, value_kind


def plan_single_input(prompt: str, answer: str) -> AnswerSlotPlan | None:
    evidence = answer.strip()
    candidate = _EXPLANATORY_PREFIX.sub("", evidence)
    if _requires_multiple_inputs(prompt, candidate):
        return None

    prefix = ""
    suffix = ""
    assignment = _ASSIGNMENT.match(candidate)
    predicate = _PREDICATE.match(candidate)
    if assignment:
        label, operator, candidate = assignment.groups()
        prefix = f"{label} {operator} "
    elif predicate:
        prefix, candidate, suffix = predicate.groups()
        prefix = f"{prefix} "
        if not suffix.endswith(".") and prompt.strip() == f"{evidence}.":
            suffix += "."
    unit = _UNIT_SUFFIX.match(candidate)
    if unit:
        candidate, unit_suffix = unit.groups()
        suffix = f"{unit_suffix}{suffix}"

    raw, normalized, value_kind = atomic_answer(candidate)
    if not raw:
        return None
    return AnswerSlotPlan(
        unit_key="answer-1",
        display_prefix=prefix,
        display_suffix=suffix,
        answer_raw=raw,
        answer_normalized=normalized,
        value_kind=value_kind,
        compare_method="VALUE",
        source_evidence=evidence,
    )


def validate_answer_slot(slot: AnswerSlotPlan) -> list[dict[str, str]]:
    issues: list[dict[str, str]] = []
    if not slot.answer_raw.strip():
        issues.append({"code": "EMPTY_ANSWER", "message": "답안 값이 비어 있습니다."})
    if len(slot.answer_raw) > 80:
        issues.append({"code": "ANSWER_TOO_LONG", "message": "답안 값은 80자를 넘을 수 없습니다."})
    rendered = "".join((slot.display_prefix, slot.answer_raw, slot.display_suffix))
    if _SENTENCE_MARKER.search(rendered):
        issues.append({"code": "SENTENCE_MARKER", "message": "답안에는 설명 문구를 포함할 수 없습니다."})
    if _TERMINAL_EXPLANATION.search(slot.answer_raw):
        issues.append({"code": "TERMINAL_EXPLANATION", "message": "답안에는 설명형 종결어를 포함할 수 없습니다."})
    if slot.answer_normalized and (
        slot.answer_normalized in _normalize_display(slot.display_prefix)
        or slot.answer_normalized in _normalize_display(slot.display_suffix)
    ):
        issues.append({"code": "ANSWER_EMBEDDED_IN_DISPLAY", "message": "표시 문구에 완전한 답안이 포함되어 있습니다."})
    return issues


def compare_answer_unit(unit: dict, submitted: str) -> bool:
    expected = str(unit.get("answer_normalized") or "")
    if not expected:
        return False
    _, actual, _ = atomic_answer(submitted)
    return actual == expected


def _requires_multiple_inputs(prompt: str, answer: str) -> bool:
    if not _MULTIPLE_LABELED_CLAUSES.fullmatch(answer):
        return False
    labels = re.findall(r"([^\s은는]+)(?:은|는)", answer)
    return len(labels) >= 2 and all(label in prompt for label in labels[:2])


def _normalize_display(value: str) -> str:
    return atomic_answer(value)[1]
