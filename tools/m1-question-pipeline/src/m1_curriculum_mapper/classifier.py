import re

from .models import Classification


MAJOR_UNIT = "자료와 가능성"


def _result(middle, small, status, confidence, rule, reason):
    return Classification(MAJOR_UNIT, middle, small, status, confidence, rule, reason)


def classify_question(topic_name: str, question_text: str) -> Classification:
    topic = (topic_name or "").strip()
    text = (question_text or "").strip()

    if "줄기와 잎" in topic or "줄기와 잎" in text:
        return _result("자료의 정리와 해석", "줄기와 잎그림", "DIRECT", 0.99,
                       "STEM_LEAF", "줄기와 잎그림 표현 형식이 명시됨")

    mean_action = re.search(r"평균(?:값)?(?:을|은|이|의 값)?\s*(?:구하|구하여|계산하|비교하|구하면|구할)", text)
    if "평균 구하기" in topic or mean_action:
        return _result("자료의 정리와 해석", "대푯값", "DIRECT", 0.92,
                       "REPRESENTATIVE_VALUE", "평균을 실제로 계산하거나 비교하는 활동")

    if any(term in topic for term in ("대푯값", "중앙값", "최빈값")):
        return _result("자료의 정리와 해석", "대푯값", "DIRECT", 0.96,
                       "REPRESENTATIVE_VALUE", "대푯값·중앙값·최빈값 개념이 명시됨")

    graph_terms = ("히스토그램", "도수분포다각형", "상대도수의 분포를 나타낸 그래프")
    if any(term in topic or term in text for term in graph_terms):
        return _result("공학도구의 이용", "그래프로 나타내고 해석하기", "SUPPORT", 0.82,
                       "GRAPH_ACTIVITY", "그래프 작성·복원·해석 활동이며 공학도구 사용은 미명시")

    comparison_terms = ("두 집단", "두 도수분포", "전체 도수가 다른", "전체도수가 다른")
    if any(term in topic or term in text for term in comparison_terms):
        return _result("공학도구의 이용", "통계자료의 처리", "SUPPORT", 0.72,
                       "STATISTICAL_COMPARISON", "복수 집단의 자료를 분석하고 결과를 비교하는 활동")

    if "통계적 탐구" in topic or "통계자료의 처리" in topic:
        return _result("공학도구의 이용", "통계자료의 처리", "SUPPORT", 0.78,
                       "STATISTICAL_INQUIRY", "통계 탐구·자료 처리 성취기준과 연결됨")

    table_terms = ("도수분포표", "도수분표표", "상대도수", "계급값", "도수")
    if any(term in topic or term in text for term in table_terms):
        return _result("자료의 정리와 해석", "도수분포표와 상대도수", "DIRECT", 0.95,
                       "FREQUENCY_TABLE", "도수분포표·계급·상대도수의 계산 또는 해석")

    return _result("", "", "REVIEW", 0.0, "NO_RULE", "명시적인 소단원 분류 근거가 부족함")


def legacy_classification(topic_name: str) -> tuple[str, str]:
    if "줄기와 잎" in (topic_name or ""):
        return "자료의 정리와 해석", "줄기와 잎그림"
    return "자료의 정리와 해석", "도수분포표와 상대도수"
