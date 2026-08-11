from .catalog import by_small_name
from .classifier import classify_question
from .models import Classification


def _classification(small_name: str, status: str, confidence: float, rule: str, reason: str) -> Classification:
    unit = by_small_name()[small_name]
    return Classification(unit.major_unit_name, unit.middle_unit_name, unit.small_unit_name,
                          status, confidence, rule, reason, unit.curriculum_unit_id)


def classify_m1_question(source_unit: str, topic_code: str, topic_name: str, question_text: str) -> Classification:
    unit = str(source_unit).zfill(2)
    topic = topic_name or ""
    text = question_text or ""
    combined = f"{topic} {text}"

    if unit == "01":
        if any(word in combined for word in ("최대공약수", "최소공배수", "공약수", "공배수", "서로소")):
            return _classification("최대공약수와 최소공배수", "DIRECT", 0.99, "UNIT01_GCD_LCM", "공약수·공배수 개념")
        return _classification("소인수 분해", "DIRECT", 0.96, "UNIT01_FACTOR", "01단원 소인수·약수·거듭제곱 영역")

    if unit == "02":
        if str(topic_code).startswith(("2015:", "2022:")) and "01-05" in str(topic_code):
            multiplication_symbols = ("\\times", "\\div", "\\cdot", "×", "÷")
            if any(word in text for word in ("곱셈", "나눗셈", "역수")) or any(symbol in text for symbol in multiplication_symbols):
                return _classification("정수, 유리수의 곱셈/나눗셈", "DIRECT", 0.90, "UNIT02_STANDARD_MUL_DIV", "사칙계산 성취기준에서 곱셈·나눗셈 기호 또는 활동 감지")
            return _classification("정수, 유리수의 덧셈/뺄셈", "DIRECT", 0.85, "UNIT02_STANDARD_ADD_SUB", "사칙계산 성취기준에서 곱셈·나눗셈 증거가 없는 덧셈·뺄셈 활동")
        if any(word in combined for word in ("덧셈", "뺄셈")) and not any(word in combined for word in ("곱셈", "나눗셈", "사칙", "거듭제곱")):
            return _classification("정수, 유리수의 덧셈/뺄셈", "DIRECT", 0.96, "UNIT02_ADD_SUB", "덧셈·뺄셈 활동")
        if any(word in combined for word in ("곱셈", "나눗셈", "역수", "사칙", "분배법칙", "거듭제곱", "혼합 계산")):
            return _classification("정수, 유리수의 곱셈/나눗셈", "DIRECT", 0.92, "UNIT02_MUL_DIV", "곱셈·나눗셈 또는 사칙 혼합 활동")
        return _classification("유리수의 대소 관계", "DIRECT", 0.90, "UNIT02_ORDER", "정수·유리수 개념, 수직선, 절댓값 또는 대소 관계")

    if unit == "03":
        equation_markers = ("방정식", "등식", "이항", "방정식의 해", "해가 ", "과부족", "원가", "정가", "농도", "속력")
        numeric_code = int(topic_code) if str(topic_code).isdigit() else 0
        if any(word in combined for word in equation_markers) or numeric_code >= 7103037:
            return _classification("일차방정식", "DIRECT", 0.96, "UNIT03_EQUATION", "방정식·등식 또는 일차방정식 활용")
        return _classification("문자의 사용과 식의 계산", "DIRECT", 0.96, "UNIT03_EXPRESSION", "문자 사용·식의 값·일차식 계산")

    if unit == "04":
        if any(word in combined for word in ("정비례", "반비례", "y=ax", "y=a/x", "비례 관계")):
            return _classification("정비례와 반비례", "DIRECT", 0.98, "UNIT04_PROPORTION", "정비례·반비례 관계")
        return _classification("좌표평면과 그래프", "DIRECT", 0.93, "UNIT04_COORDINATE", "좌표·순서쌍 또는 일반 그래프 해석")

    if unit == "05":
        if any(word in combined for word in ("작도", "합동", "삼각형이 하나로 정해", "세 변의 길이 사이")):
            return _classification("작도와 합동", "DIRECT", 0.97, "UNIT05_CONSTRUCTION", "작도·삼각형 결정조건·합동")
        return _classification("기본 도형", "DIRECT", 0.95, "UNIT05_BASIC_GEOMETRY", "점·선·면·각·위치 관계")

    if unit == "06":
        return _classification("평면도형의 성질", "DIRECT", 0.99, "UNIT06_PLANE", "06단원 다각형·원·부채꼴")

    if unit == "07":
        return _classification("입체도형의 성질", "DIRECT", 0.99, "UNIT07_SOLID", "07단원 다면체·회전체·겉넓이·부피")

    if unit == "08":
        stats = classify_question("" if str(topic_code).startswith(("2015:", "2022:")) else topic, text)
        if not stats.small_unit:
            return _classification("도수분포표와 상대도수", "SUPPORT", 0.60, "UNIT08_FALLBACK", "08단원 통계 문항의 보수적 기본 배정")
        small_name = "그래프를 나타내고 해석하기" if stats.small_unit == "그래프로 나타내고 해석하기" else stats.small_unit
        return _classification(small_name, stats.mapping_status, stats.mapping_confidence,
                               stats.matched_rule, stats.mapping_reason)

    return Classification("", "", "", "REVIEW", 0.0, "UNKNOWN_UNIT", "지원하지 않는 원천 단원", "")


def _record_source_unit(metadata: dict) -> str:
    unit = str(metadata.get("questionUnit") or metadata.get("sourceUnit") or "").zfill(2)
    if unit in {f"{number:02d}" for number in range(1, 9)}:
        return unit
    codes = metadata.get("achievementStandardCodes2022") or []
    code = str(codes[0]) if codes else ""
    if len(code) >= 5 and code.startswith("9수"):
        domain = code[2:4]
        sequence = int(code[-2:]) if code[-2:].isdigit() else 0
        if domain == "01": return "01" if sequence <= 2 else "02"
        if domain == "02": return "03" if sequence <= 4 else "04"
        if domain == "03": return "05" if sequence <= 4 else ("06" if sequence <= 6 else "07")
        if domain == "04": return "08"
    return ""


def classify_record(record: dict) -> tuple[Classification, dict]:
    """Select one curriculum unit from all source evidence carried by a record."""
    metadata = record.get("sourceMetadata") or {}
    topic_code = str(metadata.get("sourceTopicCode") or "")
    topic_name = str(metadata.get("sourceTopicName") or "")
    answer_spec = record.get("answerSpec") or {}
    final_answer = " ".join(str(value.get("raw") or "") for value in answer_spec.get("finalAnswer") or [] if isinstance(value, dict))
    evidence_text = " ".join(filter(None, (
        str(record.get("promptText") or ""),
        str(answer_spec.get("solutionText") or ""),
        final_answer,
    )))
    classification = classify_m1_question(_record_source_unit(metadata), topic_code, topic_name, evidence_text)
    if classification.major_unit == "자료의 가능성":
        classification = Classification("자료와 가능성", classification.middle_unit, classification.small_unit,
                                        classification.mapping_status, classification.mapping_confidence,
                                        classification.matched_rule, classification.mapping_reason,
                                        classification.curriculum_unit_id)
    audit = {
        "candidates": [classification.to_dict()] if classification.curriculum_unit_id else [],
        "selectedRule": classification.matched_rule,
        "confidence": classification.mapping_confidence,
        "reason": classification.mapping_reason,
    }
    return classification, audit
