from __future__ import annotations

import json
import zipfile
from pathlib import Path


def iter_raw_json(root: Path):
    root = Path(root)
    for path in sorted(root.rglob("*.json")):
        try:
            yield str(path), json.loads(path.read_text(encoding="utf-8-sig"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError):
            continue
    for archive_path in sorted(root.rglob("*.zip")):
        try:
            archive = zipfile.ZipFile(archive_path)
        except (OSError, zipfile.BadZipFile):
            continue
        with archive:
            for member in sorted(archive.namelist()):
                if not member.lower().endswith(".json"):
                    continue
                try:
                    yield f"{archive_path}!{member}", json.loads(archive.read(member).decode("utf-8-sig"))
                except (KeyError, UnicodeDecodeError, json.JSONDecodeError):
                    continue


def is_m1_math_30(raw: dict) -> bool:
    info = raw.get("raw_data_info") or {}
    return info.get("school") == "중학교" and info.get("grade") == "1학년" and info.get("subject") == "수학"


def is_m1_110_111(raw: dict) -> bool:
    info = raw.get("question_info") or {}
    while isinstance(info, list):
        info = info[0] if info else {}
    return isinstance(info, dict) and info.get("question_grade") == "M1" and str(info.get("question_unit") or "").zfill(2) in {f"{i:02d}" for i in range(1, 9)}
