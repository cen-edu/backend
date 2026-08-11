from __future__ import annotations

from pathlib import Path

from .models import InputLayout


REQUIRED_DATASET_DIRS = {
    "28": "28.교과단계별 교과 데이터",
    "30": "30.수학교과풀이과정데이터",
    "110": "110.수학 과목 자동풀이 데이터",
    "111": "111.수학 과목 문제 생성 데이터",
}

OPTIONAL_CACHE_FILES = (
    "step_fill_accepted.jsonl",
    "step_fill_rejected.jsonl",
    "essay_accepted.jsonl",
)


def discover_input_layout(data_root: Path) -> InputLayout:
    data_root = Path(data_root)
    optional_caches = {
        cache_file.removesuffix(".jsonl"): path
        for cache_file in OPTIONAL_CACHE_FILES
        if (path := data_root / cache_file).is_file()
    }
    return InputLayout(
        data_root=data_root,
        source28=data_root / REQUIRED_DATASET_DIRS["28"],
        source30=data_root / REQUIRED_DATASET_DIRS["30"],
        source110=data_root / REQUIRED_DATASET_DIRS["110"],
        source111=data_root / REQUIRED_DATASET_DIRS["111"],
        step_fill_accepted=optional_caches.get("step_fill_accepted"),
        step_fill_rejected=optional_caches.get("step_fill_rejected"),
        essay_accepted=optional_caches.get("essay_accepted"),
    )


def validate_input_layout(layout: InputLayout) -> list[dict[str, str]]:
    issues: list[dict[str, str]] = []
    for source_id, source_path in (
        ("28", layout.source28),
        ("30", layout.source30),
        ("110", layout.source110),
        ("111", layout.source111),
    ):
        if not source_path.is_dir():
            issues.append({"code": f"SOURCE_{source_id}_MISSING", "path": str(source_path)})
        elif not _contains_json_or_zip(source_path):
            issues.append({"code": f"SOURCE_{source_id}_EMPTY", "path": str(source_path)})
    return issues


def _contains_json_or_zip(source_path: Path) -> bool:
    return any(
        path.is_file() and path.suffix.lower() in {".json", ".zip"}
        for path in source_path.rglob("*")
    )
