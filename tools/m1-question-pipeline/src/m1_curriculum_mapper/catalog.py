from dataclasses import asdict, dataclass

@dataclass(frozen=True)
class CurriculumUnit:
    curriculum_unit_id: str
    grade: str
    semester: str
    subject: str
    major_unit_code: str
    major_unit_name: str
    middle_unit_code: str
    middle_unit_name: str
    small_unit_code: str
    small_unit_name: str
    display_order: int
    source_system: str = "EBSMATH"
    source_url: str = "https://www.ebsmath.co.kr/siteMap?currentGrdCd=EGRD#"

    def to_dict(self):
        return asdict(self)


_ROWS = (
    ("221100", "수와 연산", "221110", "소인수 분해", "221111", "소인수 분해"),
    ("221100", "수와 연산", "221110", "소인수 분해", "221112", "최대공약수와 최소공배수"),
    ("221100", "수와 연산", "221120", "정수와 유리수", "221121", "유리수의 대소 관계"),
    ("221100", "수와 연산", "221120", "정수와 유리수", "221122", "정수, 유리수의 덧셈/뺄셈"),
    ("221100", "수와 연산", "221120", "정수와 유리수", "221123", "정수, 유리수의 곱셈/나눗셈"),
    ("221200", "변화와 관계", "221210", "문자와 식", "221211", "문자의 사용과 식의 계산"),
    ("221200", "변화와 관계", "221210", "문자와 식", "221212", "일차방정식"),
    ("221200", "변화와 관계", "221220", "그래프와 비례관계", "221221", "좌표평면과 그래프"),
    ("221200", "변화와 관계", "221220", "그래프와 비례관계", "221222", "정비례와 반비례"),
    ("221300", "도형과 측정", "221310", "기본 도형", "221311", "기본 도형"),
    ("221300", "도형과 측정", "221310", "기본 도형", "221312", "작도와 합동"),
    ("221300", "도형과 측정", "221320", "평면도형과 입체도형", "221321", "평면도형의 성질"),
    ("221300", "도형과 측정", "221320", "평면도형과 입체도형", "221322", "입체도형의 성질"),
    ("221400", "자료와 가능성", "221410", "자료의 정리와 해석", "221411", "대푯값"),
    ("221400", "자료와 가능성", "221410", "자료의 정리와 해석", "221412", "줄기와 잎그림"),
    ("221400", "자료와 가능성", "221410", "자료의 정리와 해석", "221413", "도수분포표와 상대도수"),
    ("221400", "자료와 가능성", "221420", "공학도구의 이용", "221421", "통계자료의 처리"),
    ("221400", "자료와 가능성", "221420", "공학도구의 이용", "221422", "그래프를 나타내고 해석하기"),
)


def curriculum_units() -> list[CurriculumUnit]:
    return [CurriculumUnit(f"EBS-M1-MATH-{small_code}", "M1", semester, "수학",
                           major_code, major_name, middle_code, middle_name,
                           small_code, small_name, index)
            for index, (major_code, major_name, middle_code, middle_name, small_code, small_name)
            in enumerate(_ROWS, start=1)
            for semester in ("1" if major_code in {"221100", "221200"} else "2",)]


def by_small_name() -> dict[str, CurriculumUnit]:
    return {row.small_unit_name: row for row in curriculum_units()}
