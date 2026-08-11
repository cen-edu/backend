from __future__ import annotations

import copy
import re

from .answer_slots import atomic_answer, plan_single_input, validate_answer_slot


_CIRCLED_LABELS = "①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳"
_KOREAN_CIRCLED_LABELS = "㉠㉡㉢㉣㉤㉥㉦㉧㉨㉩㉪㉫㉬㉭㉮㉯"
_CHOICE_MARKER = re.compile(
    rf"(?<!\S)(?P<label>[{_CIRCLED_LABELS}{_KOREAN_CIRCLED_LABELS}]|[0-9]+[.)]|[A-Za-zㄱ-ㅎ][.)])\s*"
)
_MULTIPLE_CLAUSES = re.compile(r".+?(?:은|는)\s*.+?이고\s*.+?(?:은|는)\s*.+?이다\s*(?:[.。]\s*)?$")
_MULTIPLE_ASSIGNMENTS = re.compile(r"^\s*\S+\s*[=:]\s*.+?(?:,|;|그리고|및)\s*\S+\s*[=:]\s*.+$")
_MULTIPLE_VALUE_PROMPT = re.compile(r"(?:두|2)\s*(?:값|수|개|가지|답)|각각")
_COMMA_SEPARATED_VALUES = re.compile(r"^\s*[^,]+(?:\s*,\s*[^,]+)+\s*$")


def materialize_30_question(record: dict) -> tuple[dict | None, list[dict]]:
    """Turn a normalized dataset-30 row into one independently gradable input."""
    output = copy.deepcopy(record)
    metadata = output.setdefault("sourceMetadata", {})
    _preserve_source_evidence(output)
    question_type = str(metadata.get("questionType1") or "").strip()
    answers = _answer_blocks(output)
    if _is_multiple_choice(question_type):
        return _materialize_multiple_choice(output, answers)
    if _is_short_input(question_type):
        return _materialize_short_input(output, answers)
    return None, [_issue("UNSUPPORTED_QUESTION_TYPE", "30번 문항 유형을 객관식 또는 주관식으로 식별할 수 없습니다.", "sourceMetadata.questionType1")]


def _materialize_multiple_choice(record: dict, answers: list[str]) -> tuple[dict | None, list[dict]]:
    choices, prompt = _parse_choices(record)
    if len(choices) < 2 or len({choice["text"] for choice in choices}) < 2:
        return None, [_issue("CHOICE_OPTIONS_INVALID", "객관식 문항에는 서로 다른 선택지가 두 개 이상 필요합니다.", "choiceOptions")]
    if len(answers) != 1:
        return None, [_issue("MULTIPLE_FINAL_ANSWERS", "객관식 문항에는 정답 블록이 정확히 하나여야 합니다.", "answerSpec.finalAnswer")]

    selected = _select_choice(choices, answers[0])
    if selected is None:
        return None, [_issue("ANSWER_NOT_A_CHOICE", "정답은 선택지 레이블 또는 선택지 값 하나와 일치해야 합니다.", "answerSpec.finalAnswer")]

    choice, accepted_value = selected
    raw, normalized, value_kind = atomic_answer(accepted_value)
    answer_spec = copy.deepcopy(record.get("answerSpec") or {})
    answer_spec["finalAnswer"] = [{"raw": answers[0]}]
    answer_spec["units"] = [{
        "unitId": "MAIN", "unitType": "CHOICE", "compareMethod": "VALUE",
        "displayPrefix": "", "displaySuffix": "", "selectionMode": "SINGLE",
        "accepted": [{"raw": raw, "normalized": normalized, "valueKind": value_kind}],
        "sourceEvidence": answers[0],
    }]
    record["questionTypeCode"] = "MULTIPLE_CHOICE"
    record["choiceOptions"] = {"selectionMode": "SINGLE", "options": choices}
    record["answerSpec"] = answer_spec
    _replace_prompt(record, prompt)
    return record, []


def _materialize_short_input(record: dict, answers: list[str]) -> tuple[dict | None, list[dict]]:
    if len(answers) != 1:
        return None, [_issue("MULTIPLE_FINAL_ANSWERS", "주관식 문항에는 정답 블록이 정확히 하나여야 합니다.", "answerSpec.finalAnswer")]
    answer = answers[0]
    if _contains_multiple_logical_inputs(str(record.get("promptText") or ""), answer):
        return None, [_issue("MULTIPLE_LOGICAL_INPUTS", "독립적인 답안이 둘 이상이므로 하나의 입력 단위로 만들 수 없습니다.", "answerSpec.finalAnswer")]
    slot = plan_single_input(str(record.get("promptText") or ""), answer)
    if slot is None:
        return None, [_issue("ANSWER_SLOT_UNPLANNABLE", "하나의 독립 답안 단위를 만들 수 없습니다.", "answerSpec.finalAnswer")]
    slot_issues = validate_answer_slot(slot)
    if slot_issues:
        return None, [{**issue, "path": "answerSpec.finalAnswer"} for issue in slot_issues]

    answer_spec = copy.deepcopy(record.get("answerSpec") or {})
    answer_spec["finalAnswer"] = [{"raw": answer}]
    answer_spec["units"] = [{
        "unitId": "MAIN", "unitType": "BLANK", "compareMethod": "VALUE",
        "displayPrefix": slot.display_prefix, "displaySuffix": slot.display_suffix,
        "accepted": [{"raw": slot.answer_raw, "normalized": slot.answer_normalized, "valueKind": slot.value_kind}],
        "sourceEvidence": slot.source_evidence,
    }]
    record["questionTypeCode"] = "SHORT_INPUT"
    record["choiceOptions"] = None
    record["answerSpec"] = answer_spec
    return record, []


def _parse_choices(record: dict) -> tuple[list[dict], str]:
    evidence = (record.get("sourceMetadata") or {}).get("sourceEvidence") or {}
    choice_blocks = evidence.get("choiceBlocks") or evidence.get("choices") or []
    if isinstance(choice_blocks, str):
        choice_blocks = [choice_blocks]
    prompt = str(record.get("promptText") or "")
    choice_source = [str(block) for block in choice_blocks if str(block).strip()]
    source = "\n".join(choice_source) if choice_source else prompt
    choices = _parse_strict_choice_list(source, allow_prompt_prefix=not choice_source)
    if choices:
        choices = _display_order(choices)
    return choices, prompt if choice_source else _prompt_before_choice_list(prompt)


def _parse_strict_choice_list(source: str, *, allow_prompt_prefix: bool = False) -> list[dict]:
    source = re.sub(r"^\s*보기\s*[:：]?\s*", "", source)
    markers = list(_CHOICE_MARKER.finditer(source))
    if not markers or (source[:markers[0].start()].strip() and not allow_prompt_prefix):
        return []
    choices = []
    for index, marker in enumerate(markers):
        text_end = markers[index + 1].start() if index + 1 < len(markers) else len(source)
        text = source[marker.end():text_end].strip()
        if not text or _is_enumeration_only(text):
            return []
        label = marker.group("label")
        choices.append({"optionId": _option_id(label), "label": label, "text": text, "displayOrder": len(choices) + 1})
    return choices


def _prompt_before_choice_list(prompt: str) -> str:
    markers = list(_CHOICE_MARKER.finditer(prompt))
    return prompt[:markers[0].start()].rstrip() if markers else prompt


def _is_enumeration_only(text: str) -> bool:
    return bool(re.fullmatch(rf"[~∼]\s*[{_CIRCLED_LABELS}{_KOREAN_CIRCLED_LABELS}0-9A-Za-zㄱ-ㅎ.)]+", text))


def _display_order(choices: list[dict]) -> list[dict]:
    if all(choice["optionId"].isdigit() for choice in choices):
        choices = sorted(choices, key=lambda choice: int(choice["optionId"]))
    return [{**choice, "displayOrder": index} for index, choice in enumerate(choices, 1)]


def _select_choice(choices: list[dict], answer: str) -> tuple[dict, str] | None:
    answer_raw, answer_normalized, _ = atomic_answer(answer)
    matches: dict[int, tuple[dict, str]] = {}
    for choice in choices:
        _, option_normalized, _ = atomic_answer(choice["text"])
        if answer_raw == choice["label"]:
            matches[id(choice)] = (choice, choice["optionId"])
        if answer_normalized == option_normalized:
            matches.setdefault(id(choice), (choice, choice["text"]))
    answer_options = _parse_strict_choice_list(answer_raw)
    if len(answer_options) == 1:
        answer_option = answer_options[0]
        for choice in choices:
            _, option_normalized, _ = atomic_answer(choice["text"])
            _, answer_option_normalized, _ = atomic_answer(answer_option["text"])
            if answer_option["label"] == choice["label"] and answer_option_normalized == option_normalized:
                matches[id(choice)] = (choice, choice["optionId"])
    return next(iter(matches.values())) if len(matches) == 1 else None


def _option_id(label: str) -> str:
    if label and label[0] in _CIRCLED_LABELS:
        return str(_CIRCLED_LABELS.index(label[0]) + 1)
    return label.rstrip(".)")


def _contains_multiple_logical_inputs(prompt: str, answer: str) -> bool:
    return bool(
        _MULTIPLE_CLAUSES.fullmatch(answer)
        or _MULTIPLE_ASSIGNMENTS.fullmatch(answer)
        or (_MULTIPLE_VALUE_PROMPT.search(prompt) and _COMMA_SEPARATED_VALUES.fullmatch(answer))
    )


def _answer_blocks(record: dict) -> list[str]:
    values = []
    for item in (record.get("answerSpec") or {}).get("finalAnswer") or []:
        value = item.get("raw") if isinstance(item, dict) else item
        if value is not None and str(value).strip():
            values.append(str(value).strip())
    return values


def _preserve_source_evidence(record: dict) -> None:
    metadata = record.setdefault("sourceMetadata", {})
    evidence = copy.deepcopy(metadata.get("sourceEvidence") or {})
    prompt = str(record.get("promptText") or "").strip()
    answers = _answer_blocks(record)
    solution = str((record.get("answerSpec") or {}).get("solutionText") or "").strip()
    evidence.setdefault("questionBlocks", [prompt] if prompt else [])
    evidence.setdefault("answerBlocks", answers)
    evidence.setdefault("solutionBlocks", [solution] if solution else [])
    evidence.setdefault("choiceBlocks", evidence.get("choices") or [])
    metadata["sourceEvidence"] = evidence


def _replace_prompt(record: dict, prompt: str) -> None:
    if not prompt:
        return
    record["promptText"] = prompt
    blocks = record.get("contentBlocks") or []
    if blocks and blocks[0].get("blockKind") == "TEXT":
        blocks[0] = {**blocks[0], "text": prompt}
        record["contentBlocks"] = blocks


def _is_multiple_choice(question_type: str) -> bool:
    return any(token in question_type.lower() for token in ("객관", "선택", "multiple"))


def _is_short_input(question_type: str) -> bool:
    return any(token in question_type.lower() for token in ("주관", "단답", "short"))


def _issue(code: str, message: str, path: str) -> dict:
    return {"code": code, "message": message, "path": path}
