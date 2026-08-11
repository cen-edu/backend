import re


STANDARD_CODE = re.compile(r"9수\d{2}-\d{2}")


def achievement_codes(values) -> list[str]:
    if not isinstance(values, list):
        values = [values] if values else []
    return sorted({code for value in values for code in STANDARD_CODE.findall(str(value))})


def source_topic_key(dataset_id: str, *, topic_code: str = "", standards_2022=None) -> str:
    dataset = str(dataset_id)
    if dataset == "30":
        codes = achievement_codes(standards_2022)
        if not codes:
            raise ValueError("30번 데이터에는 2022 성취기준 코드가 필요합니다")
        return f"30:2022:{'+'.join(codes)}"
    code = str(topic_code).strip()
    if not code:
        raise ValueError(f"{dataset}번 데이터에는 토픽 코드가 필요합니다")
    return f"{dataset}:{code}"
