from __future__ import annotations

import copy
import re
from typing import Any


ESSAY_INSTRUCTION = "사용한 개념과 풀이 과정을 순서대로 서술하시오."
TABLE_RE = re.compile(r"(<table\b.*?(?:</table>|$))", re.IGNORECASE | re.DOTALL)
CIRCLED_CHOICE_RE = re.compile(r"[①②③④⑤⑥⑦⑧⑨⑩]")
CIRCLED_LABELS = tuple("①②③④⑤⑥⑦⑧⑨⑩")
UNIT_PARENS_RE = re.compile(r"\(\s*(mm|cm|km|m|mg|kg|g|mL|L|도|°)\s*\)", re.IGNORECASE)
DIRECT_ANSWER_RE = re.compile(r"^\s*따라서\s*([^\n{}=]+?)\s*=\s*(\{\{[^{}]+\}\})\s*이다\.?\s*$")
REPEATED_PREFIX_RE = re.compile(r"^(.{20,}?[.!?])\s*\1", re.DOTALL)
METHOD_HINT_RE = re.compile(r"^\s*(?:최대공약수|최소공배수)를?\s*구하는\s*방법\b")
INLINE_METHOD_HINT_RE = re.compile(r"\s+(?:최대공약수|최소공배수)를?\s*구하는\s*방법\b.*$", re.DOTALL)


def _compact(value: Any) -> str:
    return re.sub(r"\s+", " ", str(value or "")).strip()


def _visible_key(value: Any) -> str:
    return re.sub(r"\s+", "", str(value or ""))


def _collapse_repeated_prefix(value: str) -> str:
    value = value.lstrip(". ")
    while True:
        collapsed = REPEATED_PREFIX_RE.sub(r"\1", value, count=1)
        if collapsed == value:
            return value
        value = collapsed


def _strip_figure_caption_suffix(value: str, captions: list[str]) -> str:
    text = _compact(value)
    for caption in sorted((_compact(item) for item in captions if _compact(item)), key=len, reverse=True):
        if text.endswith(caption):
            prefix = text[:-len(caption)].rstrip(" \n:;")
            if len(_visible_key(prefix)) >= 6:
                return prefix
    return text


def is_objective_source(record: dict) -> bool:
    """Identify 111 source questions whose semantics are multiple choice."""
    if not str(record.get("sourceRef") or "").startswith("111:"):
        return False
    metadata = record.get("sourceMetadata") if isinstance(record.get("sourceMetadata"), dict) else {}
    source_type = str(metadata.get("questionType1") or metadata.get("typesOfProblems") or "")
    if any(token in source_type for token in ("객관", "선택")):
        return True
    prompt = str(record.get("promptText") or "")
    return len(set(CIRCLED_CHOICE_RE.findall(prompt))) >= 2


def naturalize_essay_prompt(prompt: str) -> str:
    prompt = _compact(prompt)
    if prompt.endswith(ESSAY_INSTRUCTION):
        prompt = re.sub(r"(?:구하여|구하여라\.?|구하시오\.?)\s*" + re.escape(ESSAY_INSTRUCTION),
                        "구하고, " + ESSAY_INSTRUCTION, prompt)
        prompt = re.sub(r"(?:완성하여라\.?|완성하시오\.?)\s*" + re.escape(ESSAY_INSTRUCTION),
                        "완성하고, " + ESSAY_INSTRUCTION, prompt)
        prompt = re.sub(r"(?:그려라\.?|그리시오\.?)\s*" + re.escape(ESSAY_INSTRUCTION),
                        "그리고, " + ESSAY_INSTRUCTION, prompt)
        return prompt
    replacements = (
        (r"(?:하여라|하시오)\.?$", "하고, "),
        (r"그려라\.?$", "그리고, "),
        (r"구하여라\.?$", "구하고, "),
        (r"구하시오\.?$", "구하고, "),
    )
    for pattern, connector in replacements:
        if re.search(pattern, prompt):
            return re.sub(pattern, connector + ESSAY_INSTRUCTION, prompt)
    return f"{prompt.rstrip('.')}의 답을 구하고, {ESSAY_INSTRUCTION}" if prompt else ESSAY_INSTRUCTION


def _split_table_block(block: dict) -> list[dict]:
    text = str(block.get("text") or "")
    parts = TABLE_RE.split(text)
    if len(parts) == 1:
        return [copy.deepcopy(block)]
    result = []
    for part in parts:
        if not part.strip():
            continue
        item = copy.deepcopy(block)
        if part.lstrip().lower().startswith("<table"):
            item.update({"blockKind": "TABLE", "markup": part, "text": ""})
            item.pop("assetRef", None)
        else:
            item.update({"blockKind": "TEXT", "text": _compact(part)})
            item.pop("markup", None)
        result.append(item)
    return result


def _sync_single_essay_text(expanded: list[dict], prompt: str) -> None:
    text_blocks = [block for block in expanded if block.get("blockKind") == "TEXT"]
    if len(text_blocks) == 1:
        text_blocks[0]["text"] = prompt


def _normalize_stage(stage: dict) -> dict:
    stage = copy.deepcopy(stage)
    template = UNIT_PARENS_RE.sub(r"\1", str(stage.get("textTemplate") or ""))
    direct = DIRECT_ANSWER_RE.match(template)
    if direct:
        template = f"{direct.group(1).strip()}={direct.group(2)}"
        stage["contentParts"] = [
            {"type": "TEXT", "value": f"{direct.group(1).strip()}="},
            {"type": "BLANK", "blankId": direct.group(2)[2:-2]},
        ]
    else:
        for part in stage.get("contentParts") or []:
            if isinstance(part, dict) and part.get("type") == "TEXT":
                part["value"] = UNIT_PARENS_RE.sub(r"\1", str(part.get("value") or ""))
    stage["textTemplate"] = template
    return stage


def normalize_content_structure(record: dict) -> dict:
    """Normalize one canonical record for block-based rendering and storage."""
    output = copy.deepcopy(record)
    prompt = TABLE_RE.sub(" ", str(output.get("promptText") or ""))
    prompt = re.sub(r"</?br\s*/?>", " ", prompt, flags=re.IGNORECASE)
    prompt = re.sub(r"</table>", " ", prompt, flags=re.IGNORECASE)
    prompt = _collapse_repeated_prefix(_compact(prompt))
    prompt = INLINE_METHOD_HINT_RE.sub("", prompt).strip()
    if output.get("questionTypeCode") == "ESSAY":
        prompt = naturalize_essay_prompt(prompt)
    output["promptText"] = UNIT_PARENS_RE.sub(r"\1", prompt)

    expanded = []
    for block in output.get("contentBlocks") or []:
        if isinstance(block, dict):
            expanded.extend(_split_table_block(block))
    if output.get("questionTypeCode") == "ESSAY":
        _sync_single_essay_text(expanded, output["promptText"])

    figure_captions = [
        str(asset.get("altText") or "")
        for asset in output.get("assets") or [] if isinstance(asset, dict) and asset.get("altText")
    ]
    figure_captions.extend(
        str(block.get("text") or "") for block in expanded
        if block.get("blockKind") in ("FIGURE", "FIGURE_TEXT") and block.get("text")
    )
    output["promptText"] = _strip_figure_caption_suffix(output["promptText"], figure_captions)
    if output.get("questionTypeCode") == "ESSAY":
        _sync_single_essay_text(expanded, output["promptText"])
    figure_texts = {_visible_key(text) for text in figure_captions}
    result, seen, seen_texts = [], set(), []
    for block in expanded:
        kind = block.get("blockKind") or "TEXT"
        if kind == "TEXT":
            block["text"] = _strip_figure_caption_suffix(
                UNIT_PARENS_RE.sub(r"\1", str(block.get("text") or "")), figure_captions
            )
            block["text"] = INLINE_METHOD_HINT_RE.sub("", block["text"]).strip()
            if METHOD_HINT_RE.match(block["text"]):
                continue
        text_key = _visible_key(block.get("text"))
        if kind == "FIGURE_TEXT":
            continue
        if kind == "TEXT" and text_key and text_key in figure_texts:
            continue
        if kind == "TEXT" and len(text_key) >= 6 and any(text_key in previous for previous in seen_texts):
            continue
        identity = (kind, text_key, str(block.get("assetRef") or ""), str(block.get("markup") or ""))
        if identity in seen:
            continue
        seen.add(identity)
        result.append(block)
        if kind == "TEXT" and text_key:
            seen_texts.append(text_key)
    for index, block in enumerate(result):
        block["displayOrder"] = index
        block["blockId"] = block.get("blockId") or f"B{index + 1}"
    output["contentBlocks"] = result

    choice_options = copy.deepcopy(output.get("choiceOptions"))
    if isinstance(choice_options, dict):
        options = []
        for index, option in enumerate(choice_options.get("options") or []):
            item = copy.deepcopy(option)
            item["optionId"] = str(index + 1)
            item["label"] = CIRCLED_LABELS[index] if index < len(CIRCLED_LABELS) else str(index + 1)
            item["displayOrder"] = index
            options.append(item)
        choice_options["options"] = options
        output["choiceOptions"] = choice_options

    if any(block.get("blockKind") == "TABLE" for block in result):
        output["presentation"] = "WITH_TABLE"
    elif output.get("assets"):
        output["presentation"] = "WITH_FIGURE"
    else:
        output["presentation"] = "TEXT_ONLY"

    problem_data = copy.deepcopy(output.get("problemData") or {})
    stages = [_normalize_stage(stage) for stage in problem_data.get("stages") or [] if isinstance(stage, dict)]
    title_counts = {}
    for index, stage in enumerate(stages):
        title = str(stage.get("title") or "")
        title_counts[title] = title_counts.get(title, 0) + 1
        if title and title_counts[title] > 2:
            stage["title"] = f"풀이 단계 {index + 1}"
    problem_data["stages"] = stages
    output["problemData"] = problem_data
    return output
