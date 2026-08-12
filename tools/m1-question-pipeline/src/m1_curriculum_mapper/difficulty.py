from __future__ import annotations


_DIFFICULTY_CODES = {
    "1": 1, "하": 1, "low": 1, "LOW": 1,
    "2": 2, "중": 2, "mid": 2, "MEDIUM": 2,
    "3": 3, "상": 3, "high": 3, "HIGH": 3,
}


def normalize_difficulty(value: object) -> int:
    """원천 난이도를 DB 계약값 1(하), 2(중), 3(상)으로 변환한다."""
    if isinstance(value, bool):
        raise ValueError(f"지원하지 않는 난이도 값: {value!r}")
    if isinstance(value, int) and value in (1, 2, 3):
        return value
    key = str(value or "").strip()
    if key in _DIFFICULTY_CODES:
        return _DIFFICULTY_CODES[key]
    raise ValueError(f"지원하지 않는 난이도 값: {value!r}")
