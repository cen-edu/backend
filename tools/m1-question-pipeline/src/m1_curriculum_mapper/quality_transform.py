from __future__ import annotations

import copy
import hashlib
import re
from collections import Counter, defaultdict

from .answer_slots import atomic_answer
from .content_normalization import naturalize_essay_prompt, normalize_content_structure
from .question_types import materialize_30_question


LABELS = "①②③④⑤⑥⑦⑧⑨⑩"
NUMBERED_RE = re.compile(rf"(?<!\S)(?P<label>[{LABELS}]|\([1-9]\)|[1-9][.)])\s*")
UNIT_ANSWER_RE = re.compile(
    r"^\s*(?P<value>.+?)\s*(?P<unit>mm|cm|km|m|mg|kg|g|mL|L|개|명|회|바퀴|도|°|원|장)"
    r"(?P<power>\^\{?[23]\}?)?\s*$",
    re.IGNORECASE,
)
SIMPLE_BOX_RE = re.compile(r"\$?\\boxed\s*\{\s*\\phantom\s*\{[^{}]*\}\s*\}\$?")
NAMED_COORDINATE_RE = re.compile(r"(?<![A-Za-z0-9_])(?P<label>[A-Z])\s*(?P<value>\([^()]+\))")
LABELED_VALUE_RE = re.compile(r"(?P<label>[^,:\n]+?)\s*[:：]\s*(?P<value>.+?)(?=,\s*[^,:\n]+?\s*[:：]|$)")


def _raw_answer(record: dict) -> str:
    units = (record.get("answerSpec") or {}).get("units") or []
    if units:
        accepted = units[0].get("accepted") or []
        if accepted:
            first = accepted[0]
            return str(first.get("raw") if isinstance(first, dict) else first).strip()
    final = (record.get("answerSpec") or {}).get("finalAnswer") or []
    if final:
        first = final[0]
        return str(first.get("raw") if isinstance(first, dict) else first).strip()
    return ""


def _numbered_parts(value: str) -> tuple[str, list[tuple[str, str]]]:
    source = str(value or "")
    markers = list(NUMBERED_RE.finditer(source))
    circled = [marker for marker in markers if marker.group("label") in LABELS]
    if len(circled) >= 2:
        markers = circled
    markers = [marker for marker in markers if source[marker.end():marker.end() + 1] != "의"]
    if len(markers) < 2:
        return source.strip(), []
    prefix = source[:markers[0].start()].strip()
    parts = []
    for index, marker in enumerate(markers):
        end = markers[index + 1].start() if index + 1 < len(markers) else len(value)
        parts.append((marker.group("label"), source[marker.end():end].strip()))
    return prefix, parts


def _best_numbered_prompt(record: dict) -> tuple[str, list[tuple[str, str]]]:
    candidates = [str(record.get("promptText") or "")]
    candidates.extend(str(block.get("text") or "") for block in record.get("contentBlocks") or [] if isinstance(block, dict))
    candidates.extend(str(asset.get("altText") or "") for asset in record.get("assets") or [] if isinstance(asset, dict))
    parsed = [_numbered_parts(candidate) for candidate in candidates]
    return max(parsed, key=lambda item: len(item[1]), default=("", []))


def _answer_type(value: str) -> str:
    compact = value.strip()
    if re.fullmatch(r"[+-]?(?:\d+(?:\.\d+)?|\d+/\d+)", compact):
        return "NUMERIC"
    if re.search(r"[=<>+*/^]|\\(?:frac|sqrt)|[A-Za-z]", compact):
        return "SYMBOLIC_EQUIVALENCE"
    return "TEXT_SET"


def _atomic_unit(answer: str) -> tuple[str, str, str]:
    cleaned = re.sub(r"\\(?:mathrm|text)\s*\{([^{}]*)\}", r"\1", answer)
    cleaned = re.sub(r"[\$~]", "", cleaned).strip()
    match = UNIT_ANSWER_RE.fullmatch(cleaned)
    if match:
        value = match.group("value").strip()
        power = {"^2": "²", "^{2}": "²", "^3": "³", "^{3}": "³"}.get(match.group("power") or "", "")
        return value, f"{match.group('unit')}{power}", _answer_type(value)
    return answer.strip(), "", _answer_type(answer)


def _answer_spec(record: dict, answer: str, *, prefix: str = "", suffix: str = "") -> dict:
    raw, normalized, value_kind = atomic_answer(answer)
    answer_type = _answer_type(raw)
    original = copy.deepcopy(record.get("answerSpec") or {})
    original["finalAnswer"] = [{"raw": raw, "normalized": normalized, "answerType": answer_type}]
    compare_method = {"NUMERIC": "VALUE", "SYMBOLIC_EQUIVALENCE": "SUBST", "TEXT_SET": "SET"}[answer_type]
    original["units"] = [{
        "unitId": "MAIN", "unitType": "BLANK", "compareMethod": compare_method,
        "displayPrefix": prefix, "displaySuffix": suffix, "answerType": answer_type,
        "accepted": [{"raw": raw, "normalized": normalized, "valueKind": value_kind, "answerType": answer_type}],
        "sourceEvidence": answer,
    }]
    return original


def _child(record: dict, index: int, prompt: str, answer: str, *, keep_assets: bool = False) -> dict:
    child = copy.deepcopy(record)
    child["recordId"] = f"{record.get('recordId') or 'source:' + str(record.get('sourceRef') or '')}:part:{index}"
    child["promptText"] = prompt.strip()
    child["contentBlocks"] = [{"blockId": "T1", "blockKind": "TEXT", "text": prompt.strip()}]
    if keep_assets:
        child["contentBlocks"].extend(
            copy.deepcopy(block) for block in record.get("contentBlocks") or []
            if isinstance(block, dict) and block.get("blockKind") in ("FIGURE", "FORMULA", "TABLE")
        )
    else:
        child["assets"] = []
        child["presentation"] = "TEXT_ONLY"
    raw, suffix, _ = _atomic_unit(answer)
    child["answerSpec"] = _answer_spec(child, raw, suffix=suffix)
    child["questionTypeCode"] = "SHORT_INPUT"
    child["choiceOptions"] = None
    if not isinstance(child.get("generationMetadata"), dict):
        child["generationMetadata"] = {}
    child["generationMetadata"]["qualityDerivation"] = {
        "kind": "SPLIT_SHORT_INPUT", "part": index, "sourceRef": record.get("sourceRef")
    }
    return normalize_content_structure(child)


def _split_numbered(record: dict) -> list[dict]:
    prompt_prefix, prompt_parts = _best_numbered_prompt(record)
    raw_answer = _raw_answer(record)
    if len(prompt_parts) >= 2:
        for index, (label, _) in enumerate(prompt_parts[1:], 2):
            if label not in raw_answer:
                raw_answer = re.sub(rf"\{{?\({index}\)\}}?", f" {label} ", raw_answer, count=1)
    _, answer_parts = _numbered_parts(raw_answer)
    if not (2 <= len(prompt_parts) == len(answer_parts) <= 6):
        return []
    instruction = prompt_prefix or str(record.get("promptText") or "").split(prompt_parts[0][0], 1)[0].strip()
    children = []
    for index, ((_, item), (_, answer)) in enumerate(zip(prompt_parts, answer_parts), 1):
        if "다음 수의" in instruction:
            prompt = instruction.replace("다음 수의", f"{item}의", 1)
        elif "다음 식" in instruction:
            prompt = f"{item}에 대하여 {instruction.replace('다음 식을', '식을').replace('다음 식의', '식의')}"
        else:
            prompt = f"{instruction}\n{item}".strip()
        children.append(_child(record, index, prompt, answer))
    suffixes = [str(child["answerSpec"]["units"][0].get("displaySuffix") or "") for child in children]
    known_suffixes = {suffix for suffix in suffixes if suffix}
    if len(known_suffixes) == 1:
        shared = next(iter(known_suffixes))
        for child in children:
            if not child["answerSpec"]["units"][0].get("displaySuffix"):
                child["answerSpec"]["units"][0]["displaySuffix"] = shared
    return children


def _split_semantic_pair(record: dict) -> list[dict]:
    units = (record.get("answerSpec") or {}).get("units") or []
    if not units:
        return []
    first_label = re.sub(r"[\s:：]+", "", str(units[0].get("displayPrefix") or ""))
    if not first_label:
        return []
    answer = _raw_answer(record)
    match = re.fullmatch(r"(.+?)\s+([가-힣]{2,})\s*[:：]\s*(.+)", answer)
    if not match:
        return []
    first_answer, second_label, second_answer = match.groups()
    if any(len([value for value in part.split(",") if value.strip()]) > 5 for part in (first_answer, second_answer)):
        return []
    prompt = str(record.get("promptText") or "")
    children = []
    for index, (label, value) in enumerate(((first_label, first_answer), (second_label, second_answer)), 1):
        child_prompt = re.sub(rf"{re.escape(first_label)}\s*(?:와|과)\s*{re.escape(second_label)}", label, prompt)
        child_prompt = re.sub(r"찾으시오", "모두 찾으시오", child_prompt, count=1)
        children.append(_child(record, index, child_prompt, value, keep_assets=True))
    return children


def _split_named_coordinates(record: dict) -> list[dict]:
    matches = list(NAMED_COORDINATE_RE.finditer(_raw_answer(record)))
    if not (2 <= len(matches) <= 6):
        return []
    labels = [match.group("label") for match in matches]
    if len(set(labels)) != len(labels):
        return []
    return [
        _child(record, index, f"점 {match.group('label')}의 좌표를 구하시오.", match.group("value"), keep_assets=True)
        for index, match in enumerate(matches, 1)
    ]


def _split_labeled_values(record: dict) -> list[dict]:
    matches = list(LABELED_VALUE_RE.finditer(_raw_answer(record)))
    if not (2 <= len(matches) <= 6):
        return []
    labels = [re.sub(r"^\s*,\s*", "", match.group("label")).strip() for match in matches]
    values = [match.group("value").strip().rstrip(".") for match in matches]
    if any(not label or not value for label, value in zip(labels, values)):
        return []
    original_prompt = str(record.get("promptText") or "")
    children = []
    for index, (label, value) in enumerate(zip(labels, values), 1):
        prompt = original_prompt
        if len(labels) == 2:
            prompt = re.sub(rf"{re.escape(labels[0])}\s*(?:와|과)\s*{re.escape(labels[1])}", label, prompt)
        if prompt == original_prompt:
            prompt = f"{label}를 구하시오."
        child = _child(record, index, prompt, value, keep_assets=True)
        child["answerSpec"]["units"][0]["displayPrefix"] = f"{label}: "
        children.append(child)
    return children


def _extract_final_value(prompt: str, answer: str) -> str:
    if "=" not in answer or not re.search(r"(?:값|개수|길이|넓이|부피|개|수)을?\s*구", prompt):
        return answer
    cleaned = re.sub(r"\s*이다\.?\s*$", "", answer).strip()
    candidate = cleaned.rsplit("=", 1)[-1].strip()
    return candidate if candidate and len(candidate) <= 60 else answer


def _short_input_issues(record: dict) -> list[dict]:
    answer = _raw_answer(record)
    _, numbered = _numbered_parts(answer)
    labeled = list(LABELED_VALUE_RE.finditer(answer))
    issues = []
    if len(numbered) >= 2 or len(labeled) >= 2 or len(NAMED_COORDINATE_RE.findall(answer)) >= 2:
        issues.append({"code": "SHORT_INPUT_MULTIPLE_ANSWERS",
                       "message": "하나의 SHORT_INPUT에 독립 정답이 둘 이상 남아 있습니다.",
                       "path": "answerSpec.units"})
    elif len([part for part in answer.split(",") if part.strip()]) > 5:
        issues.append({"code": "SHORT_INPUT_VALUE_SET_TOO_LARGE",
                       "message": "단일 입력으로 채점하기에 값의 개수가 너무 많습니다.",
                       "path": "answerSpec.units"})
    elif len(answer) > 80 or (len(answer) > 25 and re.search(r"(?:이다|된다|이므로)\.?\s*$", answer)):
        issues.append({"code": "SHORT_INPUT_SENTENCE_ANSWER",
                       "message": "풀이 문장 전체를 정답란에 입력해야 하는 문항입니다.",
                       "path": "answerSpec.units"})
    return issues


def _normalize_single_short_input(record: dict) -> dict:
    output = copy.deepcopy(record)
    answer = _extract_final_value(str(output.get("promptText") or ""), _raw_answer(output))
    raw, suffix, _ = _atomic_unit(answer)
    units = (output.get("answerSpec") or {}).get("units") or []
    prefix = str(units[0].get("displayPrefix") or "") if units else ""
    output["answerSpec"] = _answer_spec(output, raw, prefix=prefix, suffix=suffix or (str(units[0].get("displaySuffix") or "") if units else ""))
    return normalize_content_structure(output)


def _sanitize_essay(record: dict) -> dict:
    output = copy.deepcopy(record)
    prompt = SIMPLE_BOX_RE.sub("", str(output.get("promptText") or ""))
    prompt = re.sub(r"\.\s*(?=\$?[ABC]\$?상자\s*:)", ".\n\n", prompt)
    prompt = re.sub(r"\.\s*(?=이 계산 상자를)", ".\n\n", prompt)
    output["promptText"] = naturalize_essay_prompt(prompt)
    structured = re.sub(r"\s+(?=\$?[A-C]\$?상자\s*[:：])", "\n\n", output["promptText"])
    structured = re.sub(r"\s+(?=이\s+(?:계산|새로\s+만든)\s*상자)", "\n\n", structured)
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", structured) if part.strip()]
    blocks = output.get("contentBlocks") or []
    non_text = [copy.deepcopy(block) for block in blocks
                if isinstance(block, dict) and block.get("blockKind") != "TEXT"]
    output["contentBlocks"] = [
        {"blockId": f"T{index}", "blockKind": "TEXT", "text": paragraph}
        for index, paragraph in enumerate(paragraphs, 1)
    ] + non_text
    return normalize_content_structure(output)


def essay_quality_issues(record: dict) -> list[str]:
    prompt = SIMPLE_BOX_RE.sub("", str(record.get("promptText") or ""))
    issues = []
    if re.search(r"<보기>|[㉠㉡㉢㉣㉤]|[①②③④⑤]", prompt):
        issues.append("CHOICE_OR_LABELED_LIST")
    if re.search(r"\([가-마]\)", prompt):
        issues.append("MULTI_BLANK_COMPLETION")
    if len(re.findall(r"\([1-9]\)", prompt)) >= 2:
        issues.append("MULTI_SUBQUESTION")
    if prompt.count("$") % 2:
        issues.append("UNBALANCED_MATH")
    if len(prompt) > 900:
        issues.append("PROMPT_TOO_LONG")
    if len(prompt.strip()) < 25:
        issues.append("PROMPT_TOO_SHORT")
    return issues


def _essay_group_key(record: dict) -> tuple[str, str, str]:
    mapping = next((item for item in record.get("curriculumMappings") or [] if isinstance(item, dict)), {})
    return tuple(str(mapping.get(key) or "").strip() for key in ("majorUnitName", "middleUnitName", "smallUnitName"))


def _stable_essay_key(record: dict, seed: int) -> tuple[str, str]:
    identity = str(record.get("recordId") or record.get("sourceRef") or "")
    return hashlib.sha256(f"{seed}:{identity}".encode("utf-8")).hexdigest(), identity


def _cap_essay_groups(records: list[dict], *, limit: int, seed: int) -> tuple[list[dict], list[dict]]:
    if limit <= 0:
        return [row for row in records if row.get("questionTypeCode") != "ESSAY"], [
            row for row in records if row.get("questionTypeCode") == "ESSAY"
        ]
    groups: dict[tuple[str, str, str], list[dict]] = defaultdict(list)
    non_essays = []
    for row in records:
        if str(row.get("sourceRef") or "").startswith("111:") and row.get("questionTypeCode") == "ESSAY":
            groups[_essay_group_key(row)].append(row)
        else:
            non_essays.append(row)
    kept, dropped = [], []
    for group in sorted(groups):
        ordered = sorted(groups[group], key=lambda row: _stable_essay_key(row, seed))
        kept.extend(ordered[:limit])
        dropped.extend(ordered[limit:])
    return [*non_essays, *kept], dropped


def transform_quality_records(records: list[dict], *, essay_limit: int = 20,
                              seed: int = 20260810) -> tuple[list[dict], list[dict], dict]:
    accepted, rejected = [], []
    stats = Counter()
    for source in records:
        record = copy.deepcopy(source)
        source_ref = str(record.get("sourceRef") or "")
        if source_ref.startswith("30:"):
            semantic = _split_semantic_pair(record)
            named = [] if semantic else _split_named_coordinates(record)
            labeled = [] if semantic or named else _split_labeled_values(record)
            numbered = [] if semantic or named or labeled else _split_numbered(record)
            if semantic or named or labeled or numbered:
                derived = semantic or named or labeled or numbered
                accepted.extend(derived); stats["splitSourceQuestions"] += 1; stats["splitQuestions"] += len(derived)
                continue
            if record.get("questionTypeCode") not in {"MULTIPLE_CHOICE", "SHORT_INPUT"}:
                materialized, issues = materialize_30_question(record)
                if materialized is None:
                    rejected.append({"sourceRef": source_ref, "status": "REJECTED", "issues": issues})
                    stats["rejected30"] += 1
                    continue
                record = materialized
            if record.get("questionTypeCode") == "SHORT_INPUT":
                record = _normalize_single_short_input(record)
                issues = _short_input_issues(record)
                if issues:
                    rejected.append({"sourceRef": source_ref, "recordId": record.get("recordId"),
                                     "status": "REJECTED", "issues": issues})
                    stats["rejected30"] += 1
                    continue
        if source_ref.startswith("111:") and record.get("questionTypeCode") == "ESSAY":
            issues = essay_quality_issues(record)
            if issues:
                rejected.append({"sourceRef": source_ref, "recordId": record.get("recordId"), "status": "REJECTED", "issues": [{
                    "code": "ESSAY_SOURCE_INAPPROPRIATE", "message": ", ".join(issues), "path": "promptText"
                }]})
                stats["rejectedEssay"] += 1
                continue
            record = _sanitize_essay(record)
        accepted.append(normalize_content_structure(record))
    accepted, over_limit = _cap_essay_groups(accepted, limit=essay_limit, seed=seed)
    for record in over_limit:
        rejected.append({
            "sourceRef": record.get("sourceRef"), "recordId": record.get("recordId"), "status": "REJECTED",
            "issues": [{"code": "ESSAY_GROUP_LIMIT_EXCEEDED",
                        "message": f"대단원·중단원·소단원 조합별 ESSAY 최대 {essay_limit}건을 초과했습니다.",
                        "path": "curriculumMappings"}],
        })
    stats["essayGroupLimitRejected"] = len(over_limit)
    stats["accepted"] = len(accepted)
    stats["rejected"] = len(rejected)
    return accepted, rejected, dict(stats)
