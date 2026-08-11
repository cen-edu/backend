import json
import re
import zipfile
from pathlib import Path

from .models import SourceQuestion
from .raw_sources import iter_raw_json


def _first_dict(value):
    while isinstance(value, list):
        if not value:
            return {}
        value = value[0]
    return value if isinstance(value, dict) else {}


def _parse_110_111(data: dict, dataset_id: str, source_file: str) -> SourceQuestion | None:
    info = _first_dict(data.get("question_info", {}))
    unit = str(info.get("question_unit", "")).zfill(2)
    if info.get("question_grade") != "M1" or unit not in {f"{i:02d}" for i in range(1, 9)}:
        return None
    ocr = _first_dict(data.get("OCR_info", {}))
    return SourceQuestion(str(dataset_id), source_file, str(data.get("id") or Path(source_file).stem),
                          str(data.get("question_filename", "")), "M1", str(info.get("question_term", "")),
                          unit, str(info.get("question_sector2", "")), str(info.get("question_topic", "")),
                          str(info.get("question_topic_name", "")).strip(), str(ocr.get("question_text", "")).strip())


def iter_all_110_111(root: Path, dataset_id: str):
    for path in sorted(Path(root).rglob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            continue
        row = _parse_110_111(data, dataset_id, str(path))
        if row:
            yield row
    for archive_path in sorted(Path(root).rglob("*.zip")):
        try:
            archive = zipfile.ZipFile(archive_path)
        except (OSError, zipfile.BadZipFile):
            continue
        with archive:
            for member in archive.namelist():
                if not member.lower().endswith(".json"):
                    continue
                try:
                    data = json.loads(archive.read(member).decode("utf-8-sig"))
                except (KeyError, UnicodeDecodeError, json.JSONDecodeError):
                    continue
                row = _parse_110_111(data, dataset_id, f"{archive_path}!{member}")
                if row:
                    yield row


def _texts(data: dict) -> str:
    parts = []
    for block in data.get("learning_data_info", []):
        if not isinstance(block, dict):
            continue
        if not any(name in str(block.get("class_name", "")) for name in ("문항", "이미지", "해설")):
            continue
        for item in block.get("class_info_list", []):
            if isinstance(item, dict) and item.get("text_description"):
                parts.append(str(item["text_description"]).strip())
    return "\n".join(dict.fromkeys(x for x in parts if x))


def _standard(source: dict, revision: str) -> str:
    value = source.get(f"{revision}_achievement_standard", [])
    if not isinstance(value, list):
        value = [value]
    return " ".join(str(x).strip() for x in value if str(x).strip())


def _unit_from_standard(code: str, revision: str) -> str:
    match = re.search(r"\[9수(\d{2})-(\d{2})\]", code)
    if not match:
        return ""
    domain, sequence = match.groups()
    n = int(sequence)
    if domain == "01": return "01" if n <= 2 else "02"
    if domain == "02":
        if revision == "2015": return "03"
        return "03" if n <= 4 else "04"
    if domain == "03":
        if revision == "2015": return "04"
        return "05" if n <= 4 else ("06" if n <= 6 else "07")
    if domain == "04":
        if revision == "2015": return "05" if n <= 4 else ("06" if n <= 6 else "07")
        return "08"
    if domain == "05" and revision == "2015": return "08"
    return ""


def iter_all_30(root: Path):
    for source_file, data in iter_raw_json(root):
        raw, source = data.get("raw_data_info", {}), data.get("source_data_info", {})
        if raw.get("school") != "중학교" or raw.get("grade") != "1학년" or raw.get("subject") != "수학":
            continue
        revision = "2022"
        standard = _standard(source, revision)
        unit = _unit_from_standard(standard, revision)
        if not unit:
            continue
        codes = sorted(set(re.findall(r"9수\d{2}-\d{2}", standard)))
        code = "+".join(codes) if codes else "UNKNOWN"
        source_name = str(source.get("source_data_name") or Path(source_file).stem)
        term_match = re.search(r"([12])", str(raw.get("semester", "")))
        yield SourceQuestion("30", source_file, source_name, source_name, "M1",
                             term_match.group(1) if term_match else "", unit, "", code, standard, _texts(data))
