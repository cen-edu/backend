from __future__ import annotations

import copy
import hashlib
import re
import unicodedata
from collections import defaultdict
from difflib import SequenceMatcher
from typing import Any

from .content_normalization import is_objective_source, naturalize_essay_prompt, normalize_content_structure
from .quality_transform import essay_quality_issues


ESSAY_PROMPT_SUFFIX = "사용한 개념과 풀이 과정을 순서대로 서술하시오."
_CHOICE_LABEL = re.compile(r"(?:^|\n)\s*(?:[①②③④⑤⑥⑦⑧⑨⑩⑪⑫⑬⑭⑮⑯⑰⑱⑲⑳㉠㉡㉢㉣㉤㉥㉦㉧㉨㉩㉪㉫㉬㉭㉮㉯]|(?:\(?\d+\)?|[A-Za-zㄱ-ㅎ])[.)])\s*")
_MATH_OPERATORS = frozenset("+-−*/×÷=<>^%∗∙·")


def _text(value: Any) -> str:
    return str(value).strip() if value is not None else ""


def _collect_text(value: Any) -> list[str]:
    if isinstance(value, dict):
        return [text for item in value.values() for text in _collect_text(item)]
    if isinstance(value, (list, tuple)):
        return [text for item in value for text in _collect_text(item)]
    text = _text(value)
    return [text] if text else []


def _choice_texts(value: Any) -> list[str]:
    if isinstance(value, dict):
        options = value.get("options")
        if isinstance(options, list):
            return [_text(option.get("text") or option.get("value")) for option in options if isinstance(option, dict)]
        return [_text(value.get("text") or value.get("value"))]
    if isinstance(value, list):
        return [
            _text(item.get("text") or item.get("value")) if isinstance(item, dict) else _text(item)
            for item in value
        ]
    return [_text(value)]


def _normalized_content_text(value: str) -> str:
    """Normalize visible mathematical content without erasing numeric or operator evidence."""
    value = unicodedata.normalize("NFKC", _CHOICE_LABEL.sub("", value))
    normalized = []
    for character in value:
        if character.isspace():
            continue
        if character in _MATH_OPERATORS or not unicodedata.category(character).startswith("P"):
            normalized.append(character)
    return "".join(normalized)


def _content_parts(record: dict) -> list[str]:
    metadata = record.get("sourceMetadata") if isinstance(record.get("sourceMetadata"), dict) else {}
    parts = [_text(record.get("promptText"))]
    for key in ("formula", "formulas", "formulaText", "formula_text", "mathFormula", "tableText", "figureText", "requestedOperation", "operation"):
        parts.extend(_collect_text(record.get(key)))
    parts.extend(_collect_text(metadata.get("requestedOperation")))
    parts.extend(_choice_texts(record.get("choiceOptions")))
    for block in record.get("contentBlocks") or []:
        if isinstance(block, dict):
            parts.extend(_collect_text(block.get("text") or block.get("value") or block.get("content")))
    return [part for part in parts if part]


def content_fingerprint(record: dict) -> str:
    """Return a stable digest for visible problem content, independent of source identifiers."""
    normalized = "\x1f".join(_normalized_content_text(part) for part in _content_parts(record) if _normalized_content_text(part))
    return hashlib.sha256(normalized.encode("utf-8")).hexdigest()


def _similarity_text(record: dict) -> str:
    return " ".join(_normalized_content_text(part) for part in _content_parts(record) if _normalized_content_text(part))


def is_probability_mapping(record: dict) -> bool:
    mappings = record.get("curriculumMappings") or []
    if len(mappings) != 1 or not isinstance(mappings[0], dict):
        return False
    mapping = mappings[0]
    major = _text(mapping.get("majorUnitName"))
    if not major:
        major = _text(mapping.get("curriculumExternalKey")).split(">", 1)[0].rsplit(":", 1)[-1]
    return major == "자료와 가능성"


def _similarity_candidates(record: dict, rows110: list[dict]) -> list[dict]:
    source_text = _similarity_text(record)
    if not source_text:
        return []
    candidates = []
    for row110 in rows110:
        score = SequenceMatcher(a=source_text, b=_similarity_text(row110)).ratio()
        if 0.80 <= score < 1.0:
            candidates.append({"sourceRef": _text(row110.get("sourceRef")), "similarity": round(score, 6)})
    return sorted(candidates, key=lambda item: (-item["similarity"], item["sourceRef"]))


def select_probability_step_fill(rows111: list[dict], rows110: list[dict]) -> tuple[list[dict], list[dict]]:
    """Select non-duplicate 111 probability rows and retain duplicate evidence for audit."""
    by_fingerprint: dict[str, list[dict]] = defaultdict(list)
    for row in rows110:
        by_fingerprint[content_fingerprint(row)].append(row)
    for matches in by_fingerprint.values():
        matches.sort(key=lambda row: _text(row.get("sourceRef")))

    selected, duplicates = [], []
    for row in sorted(rows111, key=lambda item: _text(item.get("sourceRef"))):
        if not is_probability_mapping(row) or is_objective_source(row):
            continue
        fingerprint = content_fingerprint(row)
        matches = by_fingerprint.get(fingerprint, [])
        if matches:
            duplicate = copy.deepcopy(row)
            duplicate["stepFillDuplicateEvidence"] = {
                "kind": "EXACT_CONTENT_FINGERPRINT",
                "contentFingerprint": fingerprint,
                "matchedSourceRef": _text(matches[0].get("sourceRef")),
                "matchedSourceRefs": [_text(match.get("sourceRef")) for match in matches],
            }
            duplicates.append(duplicate)
            continue
        candidate = copy.deepcopy(row)
        candidate["stepFillSelectionReason"] = "111_DATA_AND_PROBABILITY_GAP"
        similarity = _similarity_candidates(row, rows110)
        if similarity:
            candidate["stepFillSimilarityCandidates"] = similarity
        selected.append(candidate)
    return selected, duplicates


def _group_key(record: dict) -> tuple[str, str, str]:
    mapping = (record.get("curriculumMappings") or [{}])[0]
    return tuple(_text(mapping.get(key)) for key in ("majorUnitName", "middleUnitName", "smallUnitName"))


def _sample_key(record: dict, seed: int) -> tuple[str, str]:
    source_ref = _text(record.get("sourceRef"))
    digest = hashlib.sha256(f"{seed}:{source_ref}".encode("utf-8")).hexdigest()
    return digest, source_ref


def sample_essay_candidates(rows111: list[dict], *, limit: int = 20, seed: int = 20260810) -> list[dict]:
    """Choose a stable, quality-filtered sample per curriculum hierarchy path."""
    if limit <= 0:
        return []
    groups: dict[tuple[str, str, str], list[dict]] = defaultdict(list)
    for row in rows111:
        if is_objective_source(row) or essay_quality_issues(row):
            continue
        groups[_group_key(row)].append(row)
    selected = []
    for group in sorted(groups):
        selected.extend(copy.deepcopy(row) for row in sorted(groups[group], key=lambda row: _sample_key(row, seed))[:limit])
    return selected


def _source_model_answer(record: dict) -> str:
    metadata = record.get("sourceMetadata") if isinstance(record.get("sourceMetadata"), dict) else {}
    evidence = metadata.get("sourceEvidence") if isinstance(metadata.get("sourceEvidence"), dict) else {}
    return _text(evidence.get("modelAnswerText")) or _text((record.get("answerSpec") or {}).get("solutionText"))


def _essay_answer_spec(model_answer: str) -> dict:
    accepted = {"raw": model_answer, "normalized": model_answer, "valueKind": "TEXT"}
    return {
        "finalAnswer": [{"raw": model_answer, "normalized": model_answer, "answerType": "TEXT_SET"}],
        "solutionText": model_answer,
        "units": [{
            "unitId": "MAIN", "unitType": "RUBRIC", "compareMethod": "RUBRIC",
            "displayPrefix": None, "displaySuffix": None, "accepted": [accepted],
            "sourceEvidence": model_answer,
        }],
    }


def _default_rubric_items() -> list[dict]:
    return [
        {"rubricId": "R1", "criterionCode": "CONCEPT_AND_CONDITION", "criterion": "필요한 개념과 조건을 식별한다.", "displayOrder": 0},
        {"rubricId": "R2", "criterionCode": "MATHEMATICAL_PROCESS", "criterion": "타당한 수학적 과정을 제시한다.", "displayOrder": 1},
        {"rubricId": "R3", "criterionCode": "FINAL_CONCLUSION", "criterion": "최종 결론을 명확히 제시한다.", "displayOrder": 2},
    ]


def _append_essay_instruction(prompt: str) -> str:
    return naturalize_essay_prompt(prompt)


def _sync_text_content_block(record: dict) -> None:
    blocks = copy.deepcopy(record.get("contentBlocks") or [])
    for index, block in enumerate(blocks):
        if isinstance(block, dict) and block.get("blockKind") == "TEXT":
            blocks[index] = {**block, "text": record["promptText"]}
            break
    else:
        blocks.insert(0, {"blockId": "T1", "blockKind": "TEXT", "text": record["promptText"]})
    record["contentBlocks"] = blocks
    normalized = normalize_content_structure(record)
    record.update(normalized)


def materialize_essay(record: dict) -> tuple[dict | None, list[dict]]:
    """Create a no-API ESSAY derivative when source solution evidence is available."""
    model_answer = _source_model_answer(record)
    if not model_answer:
        return None, [{"code": "SOURCE_MODEL_ANSWER_MISSING", "message": "ESSAY에는 원천 모범답안이 필요합니다.", "path": "sourceMetadata.sourceEvidence.modelAnswerText"}]

    output = copy.deepcopy(record)
    source_ref = _text(output.get("sourceRef"))
    output["recordId"] = f"{_text(output.get('recordId')) or f'source:{source_ref}'}:essay"
    # ERD source_type has IMPORTED / GENERATED / RUNTIME only.
    output["sourceType"] = "RUNTIME"
    output["questionTypeCode"] = "ESSAY"
    output["promptText"] = _append_essay_instruction(_text(output.get("promptText")))
    _sync_text_content_block(output)
    output["choiceOptions"] = None
    output["problemData"] = {}
    output["answerSpec"] = _essay_answer_spec(model_answer)
    output["rubricItems"] = _default_rubric_items()
    metadata = output.setdefault("sourceMetadata", {})
    metadata["derivation"] = {"kind": "ESSAY", "sourceRef": source_ref, "method": "DETERMINISTIC"}
    output["essayCacheStatus"] = "MATERIALIZED"
    return output, []


def _cached_model_answer(cached: dict) -> str:
    answer_spec = cached.get("answerSpec") if isinstance(cached.get("answerSpec"), dict) else {}
    direct = _text(cached.get("modelAnswerText")) or _text(answer_spec.get("solutionText"))
    if direct:
        return direct
    for unit in answer_spec.get("units") or []:
        if isinstance(unit, dict) and unit.get("unitId") == "MAIN":
            for answer in unit.get("accepted") or []:
                value = answer.get("raw") if isinstance(answer, dict) else answer
                if _text(value):
                    return _text(value)
    return ""


def _cached_rubric(cached: dict) -> list[dict]:
    rubric = cached.get("rubricItems", cached.get("rubric"))
    return copy.deepcopy(rubric) if isinstance(rubric, list) else []


def _validate_cached_essay(cached: dict) -> tuple[str, str, list[dict], list[dict]]:
    issues = []
    if _text(cached.get("questionTypeCode")) != "ESSAY":
        issues.append({"code": "ESSAY_CACHE_TYPE_INVALID", "message": "캐시 문항 유형은 ESSAY여야 합니다.", "path": "questionTypeCode"})
    prompt = _text(cached.get("promptText"))
    if not prompt:
        issues.append({"code": "ESSAY_CACHE_PROMPT_MISSING", "message": "캐시 ESSAY 문항이 필요합니다.", "path": "promptText"})
    model_answer = _cached_model_answer(cached)
    if not model_answer:
        issues.append({"code": "ESSAY_CACHE_MODEL_ANSWER_MISSING", "message": "캐시 ESSAY 모범답안이 필요합니다.", "path": "answerSpec"})
    rubric = _cached_rubric(cached)
    if len(rubric) != 3 or any(not isinstance(item, dict) or not _text(item.get("criterion") or item.get("description")) for item in rubric):
        issues.append({"code": "ESSAY_CACHE_RUBRIC_INVALID", "message": "캐시 ESSAY에는 세 개의 rubric 항목이 필요합니다.", "path": "rubricItems"})
    return prompt, model_answer, rubric, issues


def resolve_essay_cache(candidates: list[dict], cached_rows: list[dict]) -> tuple[list[dict], list[dict]]:
    """Reuse valid cached ESSAY fields while retaining freshly classified source fields."""
    caches: dict[str, dict] = {}
    for cached in sorted((row for row in cached_rows if isinstance(row, dict)), key=lambda row: (_text(row.get("sourceRef")), _text(row.get("recordId")))):
        caches.setdefault(_text(cached.get("sourceRef")), cached)

    accepted, rejected = [], []
    for candidate in candidates:
        essay, issues = materialize_essay(candidate)
        if essay is None:
            rejected.append({"sourceRef": _text(candidate.get("sourceRef")), "status": "REJECTED", "issues": issues})
            continue
        cached = caches.get(_text(candidate.get("sourceRef")))
        if cached is not None:
            prompt, model_answer, rubric, cache_issues = _validate_cached_essay(cached)
            if not cache_issues:
                essay["promptText"] = prompt
                _sync_text_content_block(essay)
                essay["answerSpec"] = _essay_answer_spec(model_answer)
                essay["rubricItems"] = rubric
                essay["essayCacheStatus"] = "REUSED"
            else:
                essay["essayCacheStatus"] = "INVALID_FALLBACK"
                essay["essayCacheIssues"] = cache_issues
        accepted.append(essay)
    return accepted, rejected
