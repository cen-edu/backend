import pytest

from m1_curriculum_mapper.difficulty import normalize_difficulty


@pytest.mark.parametrize(
    ("raw", "expected"),
    [("하", 1), ("중", 2), ("상", 3), ("1", 1), (2, 2), ("HIGH", 3)],
)
def test_normalize_difficulty_uses_numeric_codes(raw, expected):
    assert normalize_difficulty(raw) == expected


def test_normalize_difficulty_rejects_unknown_value():
    with pytest.raises(ValueError):
        normalize_difficulty("알 수 없음")
