from __future__ import annotations

from pathlib import Path

from .full_classifier import classify_m1_question
from .raw_sources import is_m1_math_30, iter_raw_json
from .topic_key import achievement_codes


def _learning_rows(value) -> list[dict]:
    if isinstance(value, dict):
        return [value]
    if isinstance(value, list):
        return [row for row in value if isinstance(row, dict)]
    return []


def _source_unit(codes: list[str]) -> str:
    if not codes:
        return "08"
    domain, sequence = codes[0].split("수", 1)[1].split("-", 1)
    number = int(sequence)
    if domain == "01":
        return "01" if number <= 2 else "02"
    if domain == "02":
        return "03" if number <= 4 else "04"
    if domain == "03":
        return "05" if number <= 4 else ("06" if number <= 6 else "07")
    return "08" if domain == "04" else ""


def build_source28_index(root: Path) -> dict[str, list[dict]]:
    """Index middle-school grade-1 math reference material by curriculum unit."""
    index: dict[str, list[dict]] = {}
    for source_file, raw in iter_raw_json(root):
        if not is_m1_math_30(raw):
            continue
        source = raw.get("source_data_info") or {}
        codes = achievement_codes(source.get("2022_achievement_standard") or [])
        source_name = str(source.get("source_data_name") or Path(source_file).stem)
        for position, learning in enumerate(_learning_rows(raw.get("learning_data_info")), start=1):
            description = str(learning.get("text_description") or "").strip()
            question = str(learning.get("text_qa") or "").strip()
            answer = str(learning.get("text_an") or "").strip()
            classification = classify_m1_question(_source_unit(codes), "", " ".join(codes), " ".join((description, question)))
            if not classification.curriculum_unit_id:
                continue
            source_ref = f"28:{source_name}" if position == 1 else f"28:{source_name}:{position}"
            index.setdefault(classification.curriculum_unit_id, []).append({
                "sourceRef": source_ref,
                "achievementStandardCodes": codes,
                "description": description,
                "question": question,
                "answer": answer,
            })
    for rows in index.values():
        rows.sort(key=lambda row: row["sourceRef"])
    return index
