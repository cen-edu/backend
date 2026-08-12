from __future__ import annotations

import copy
import re
from pathlib import Path

from .topic_key import achievement_codes, source_topic_key
from .learning_guide import build_learning_guide
from .catalog import curriculum_units
from .full_classifier import classify_record
from .question_types import materialize_30_question
from .difficulty import normalize_difficulty


DATASET_CODES = {"30": "SOLUTION_PROCESS", "110": "AUTO_SOLVING", "111": "QUESTION_GENERATION"}
def _first_dict(value) -> dict:
    while isinstance(value, list):
        value = value[0] if value else {}
    return value if isinstance(value, dict) else {}


def _text_blocks(raw: dict, class_fragment: str) -> list[str]:
    values = []
    for block in raw.get("learning_data_info") or []:
        if not isinstance(block, dict) or class_fragment not in str(block.get("class_name", "")):
            continue
        for item in block.get("class_info_list") or []:
            text = item.get("text_description") if isinstance(item, dict) else None
            if text and str(text).strip():
                values.append(str(text).strip())
    return list(dict.fromkeys(values))


def normalize_110_111_record(raw: dict, dataset_id: str, source_file: str) -> dict:
    info = _first_dict(raw.get("question_info"))
    ocr = _first_dict(raw.get("OCR_info"))
    answer_info = _first_dict(raw.get("answer_info"))
    source_id = str(raw.get("id") or Path(source_file).stem)
    topic_code = str(info.get("question_topic") or "")
    prompt = str(ocr.get("question_text") or "").strip()
    figure = str(ocr.get("figure_text") or "").strip()
    blocks = [{"blockId": "T1", "blockKind": "TEXT", "text": prompt}] if prompt else []
    if figure:
        blocks.append({"blockId": "F1", "blockKind": "FIGURE_TEXT", "text": figure})
    source_metadata = {
        "datasetCode": DATASET_CODES[dataset_id], "grade": info.get("question_grade"),
        "semester": str(info.get("question_term") or ""), "questionUnit": str(info.get("question_unit") or "").zfill(2),
        "sourceTopicKey": source_topic_key(dataset_id, topic_code=topic_code),
        "sourceTopicCode": topic_code, "sourceTopicName": str(info.get("question_topic_name") or "").strip(),
        "questionType1": info.get("question_type1"), "sourceFileName": Path(source_file).name,
    }
    answer_text = str(answer_info.get("answer_text") or "").strip()
    final_answer = next(
        (str(item.get("text") or "").strip() for item in reversed(answer_info.get("answer_bbox") or [])
         if isinstance(item, dict) and item.get("type") == "answer" and str(item.get("text") or "").strip()),
        "",
    )
    if answer_text or final_answer:
        source_metadata["sourceEvidence"] = {
            "modelAnswerText": answer_text, "finalAnswer": final_answer, "sourceFile": str(source_file),
        }
    return {
        "sourceRef": f"{dataset_id}:{source_id}", "sourceType": "IMPORTED",
        "sourceDatasetCode": DATASET_CODES[dataset_id], "pipelineStage": "NORMALIZED",
        "promptText": prompt, "contentBlocks": blocks, "assets": [], "answerSpec": None,
        "choiceOptions": None, "problemData": {}, "curriculumMappings": [], "learningGuide": None,
        "difficulty": normalize_difficulty(info.get("question_difficulty")), "semester": str(info.get("question_term") or ""),
        "sourceMetadata": source_metadata,
    }


def normalize_30_record(raw: dict, source_file: str) -> dict:
    info = raw.get("raw_data_info") or {}
    source = raw.get("source_data_info") or {}
    source_id = str(source.get("source_data_name") or Path(source_file).stem)
    standards_2022 = source.get("2022_achievement_standard") or []
    standards_2015 = source.get("2015_achievement_standard") or []
    questions = _text_blocks(raw, "문항")
    answers = _text_blocks(raw, "정답")
    wrong_answers = _text_blocks(raw, "오답")
    solutions = _text_blocks(raw, "해설")
    view_choices = _text_blocks(raw, "보기")
    choices = [*answers, *wrong_answers] or view_choices
    prompt = "\n".join(questions)
    term = re.search(r"[12]", str(info.get("semester") or ""))
    answer_spec = None
    if answers or solutions:
        answer_spec = {"finalAnswer": [{"raw": value} for value in answers], "solutionText": "\n".join(solutions), "units": []}
    record = {
        "sourceRef": f"30:{source_id}", "sourceType": "IMPORTED", "sourceDatasetCode": DATASET_CODES["30"],
        "pipelineStage": "NORMALIZED", "promptText": prompt,
        "contentBlocks": [{"blockId": f"T{i}", "blockKind": "TEXT", "text": text} for i, text in enumerate(questions, 1)],
        "assets": [], "answerSpec": answer_spec, "choiceOptions": None, "problemData": {},
        "curriculumMappings": [], "learningGuide": None,
        "difficulty": normalize_difficulty(source.get("level_of_difficulty")),
        "semester": term.group(0) if term else "",
        "sourceMetadata": {
            "datasetCode": DATASET_CODES["30"], "grade": "M1", "semester": term.group(0) if term else "",
            "sourceTopicKey": source_topic_key("30", standards_2022=standards_2022),
            "achievementStandardCodes2022": achievement_codes(standards_2022),
            "achievementStandards2022": standards_2022, "achievementStandards2015": standards_2015,
            "achievementStandardBasis": "2022", "questionType1": source.get("types_of_problems"),
            "sourceFileName": Path(source_file).name,
            "sourceEvidence": {
                "questionBlocks": questions, "answerBlocks": answers,
                "solutionBlocks": solutions, "choiceBlocks": choices,
                "correctOptionBlocks": answers, "wrongOptionBlocks": wrong_answers,
                "viewOptionBlocks": view_choices,
            },
        },
    }
    materialized, issues = materialize_30_question(record)
    if materialized is not None:
        return materialized
    if source.get("types_of_problems"):
        record["normalizationIssues"] = issues
        record["pipelineStage"] = "NORMALIZATION_REVIEW"
    return record


def _external_key(unit) -> str:
    return ("EBS_MATH:2022:MIDDLE:M1:MATH:SUB:"
            f"{unit.major_unit_name}>{unit.middle_unit_name}>{unit.small_unit_name}")


def _clear_curriculum_mapping(output: dict, reason: str) -> dict:
    output["curriculumMappings"] = []
    metadata = output.setdefault("sourceMetadata", {})
    metadata.pop("curriculumUnitId", None)
    metadata.pop("curriculumSmallUnitName", None)
    metadata["curriculumMappingIssue"] = reason
    output["pipelineStage"] = "CURRICULUM_REVIEW"
    return output


def apply_curriculum_mapping(record: dict, topic_index: dict[str, dict], *, classification=None, audit: dict | None = None) -> dict:
    output = copy.deepcopy(record)
    classification, classified_audit = (classification, audit) if classification is not None else classify_record(output)
    audit = audit or classified_audit
    unit = next((row for row in curriculum_units() if row.curriculum_unit_id == classification.curriculum_unit_id), None)
    if not unit:
        return _clear_curriculum_mapping(output, "NO_VALID_CURRICULUM_UNIT")
    topic_key = output.get("sourceMetadata", {}).get("sourceTopicKey")
    topic_mapping = topic_index.get(topic_key) or {}
    output["curriculumMappings"] = [{
        "curriculumUnitId": unit.curriculum_unit_id, "curriculumExternalKey": _external_key(unit),
        "majorUnitName": unit.major_unit_name, "middleUnitName": unit.middle_unit_name, "smallUnitName": unit.small_unit_name,
        "isPrimary": True, "method": "RECORD_EVIDENCE", "mappingMethod": "RECORD_EVIDENCE",
        "confidence": float(classification.mapping_confidence),
        "evidence": {"classification": audit, "sourceTopic": {
            "sourceTopicKey": topic_key, "curriculumUnitId": topic_mapping.get("curriculum_unit_id"),
            "matchedRule": topic_mapping.get("matched_rule"), "mappingVersion": topic_mapping.get("mapping_version"),
        }},
    }]
    output["sourceMetadata"]["curriculumUnitId"] = unit.curriculum_unit_id
    output["sourceMetadata"]["curriculumSmallUnitName"] = unit.small_unit_name
    output["sourceMetadata"].pop("curriculumMappingIssue", None)
    output["pipelineStage"] = "CURRICULUM_MAPPED"
    return output


def attach_learning_guide(record: dict, source28_index: dict[str, list[dict]] | None = None) -> dict:
    output = copy.deepcopy(record)
    metadata = output.get("sourceMetadata") or {}
    title = metadata.get("curriculumSmallUnitName")
    if not title or not output.get("curriculumMappings"):
        output["pipelineStage"] = "LEARNING_GUIDE_REVIEW"
        return output
    unit_id = metadata.get("curriculumUnitId") or next(
        (mapping.get("curriculumUnitId") for mapping in output.get("curriculumMappings") or [] if mapping.get("isPrimary")),
        "",
    )
    output["learningGuide"] = build_learning_guide(output, (source28_index or {}).get(unit_id) or [])
    output["pipelineStage"] = "LEARNING_GUIDE_ENRICHED"
    return output
