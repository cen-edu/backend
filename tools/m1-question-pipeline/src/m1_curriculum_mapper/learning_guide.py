from __future__ import annotations

import re
from typing import Any


def _without_answer_leakage(text: str, answers: list[str]) -> str:
    value = re.sub(r"\$[^$]*\$", "수식", text or "")
    for answer in answers:
        if answer:
            value = value.replace(answer, "")
    value = re.sub(r"\d+(?:\.\d+)?", "", value)
    return re.sub(r"\s+", " ", value).strip(" .,:;，。")


def classify_learning_strategy(question: dict[str, Any]) -> str:
    prompt_and_solution = " ".join([
        str(question.get("promptText") or ""),
        str((question.get("answerSpec") or {}).get("solutionText") or ""),
    ])
    text = " ".join([
        prompt_and_solution,
        " ".join(str(m.get("curriculumExternalKey") or "") for m in question.get("curriculumMappings") or []),
    ])
    compact = re.sub(r"\s+", "", text)
    prompt = re.sub(r"\s+", "", prompt_and_solution)
    if "최대공약수" in compact or "최소공배수" in compact:
        if "톱니" in prompt: return "GCD_LCM_GEAR"
        if any(x in prompt for x in ("시간", "분마다", "동시에", "주기")): return "GCD_LCM_CYCLE"
        if "소인수" in prompt: return "GCD_LCM_PRIME_FACTOR"
        if "약수" in prompt: return "GCD_LCM_DIVISOR"
        if "곱" in prompt or "관계" in prompt: return "GCD_LCM_PRODUCT_RELATION"
        return "GCD_LCM_MULTIPLE"
    if "소인수분해" in compact or "소인수 분해" in compact:
        if "약수의개수" in compact or "약수개수" in compact: return "PRIME_FACTOR_DIVISOR_COUNT"
        if "소수" in compact and "합성수" in compact: return "PRIME_COMPOSITE_CLASSIFY"
        if "약수" in compact: return "PRIME_FACTOR_DIVISORS"
        return "PRIME_FACTOR_DIRECT"
    if "일차방정식" in compact:
        return "LINEAR_EQUATION_WORD" if "활용" in compact or any(x in compact for x in ("거리", "속력", "나이", "개수")) else "LINEAR_EQUATION_SOLVE"
    if "문자의사용" in compact or "식의계산" in compact:
        if "대입" in prompt: return "ALGEBRA_SUBSTITUTION"
        if "분배법칙" in prompt or "전개" in prompt: return "ALGEBRA_DISTRIBUTION"
        if "계산" in prompt or "간단히" in prompt: return "ALGEBRA_SIMPLIFY"
        return "ALGEBRA_EXPRESSION"
    if any(x in compact for x in ("정수·유리수의곱셈과나눗셈", "정수,유리수의곱셈/나눗셈")):
        if any(x in prompt for x in ("부호", "양수", "음수")): return "RATIONAL_SIGN"
        if "분수" in prompt: return "RATIONAL_FRACTION"
        return "RATIONAL_OPERATION"
    if "유리수의대소관계" in compact or "두유리수의대소관계" in compact:
        return "RATIONAL_COMPARE_ABS" if "절댓값" in prompt else "RATIONAL_COMPARE"
    if any(x in compact for x in ("정수·유리수의덧셈과뺄셈", "정수와유리수의덧셈과뺄셈", "정수,유리수의덧셈/뺄셈")):
        return "RATIONAL_ADD_SUB_ABS" if "절댓값" in prompt else "RATIONAL_ADD_SUB"
    if "도수분포표와상대도수" in compact or "줄기와잎" in compact:
        if "상대도수" in prompt: return "DATA_RELATIVE_FREQUENCY"
        if any(x in prompt for x in ("평균", "중앙값", "최빈값")): return "DATA_CENTER"
        if "줄기" in prompt: return "DATA_STEM_LEAF"
        return "DATA_TABLE_READ"
    if "대푯값" in compact: return "DATA_CENTER"
    if "기본도형" in compact:
        if "평행" in prompt: return "GEOMETRY_PARALLEL"
        if "각" in prompt: return "GEOMETRY_ANGLE"
        return "GEOMETRY_RELATION"
    if "평면도형의성질" in compact or "작도와합동" in compact:
        if "합동" in prompt or "합동" in compact: return "PLANE_CONGRUENCE"
        if "내각" in prompt or "외각" in prompt: return "PLANE_POLYGON_ANGLE"
        if "넓이" in prompt: return "PLANE_AREA"
        return "PLANE_PROPERTY"
    if "입체도형의성질" in compact:
        if "겉넓이" in prompt or "부피" in prompt: return "SOLID_MEASURE"
        if "단면" in prompt or "회전" in prompt: return "SOLID_SECTION"
        return "SOLID_PROPERTY"
    if "좌표평면과그래프" in compact:
        if "좌표" in prompt and ("축" in prompt or "사분면" in prompt): return "COORDINATE_POSITION"
        if "그래프" in prompt or "변화" in prompt: return "COORDINATE_GRAPH_READ"
        return "COORDINATE_POINT"
    if "정비례와반비례" in compact:
        if any(x in prompt for x in ("넓이", "거리", "속력")): return "PROPORTIONAL_APPLICATION"
        if "그래프" in prompt: return "PROPORTIONAL_GRAPH"
        if "식" in prompt or "관계식" in prompt: return "PROPORTIONAL_FORMULA"
        return "PROPORTIONAL_RELATION"
    return "GENERAL_CONCEPT_APPLICATION"


STRATEGY_POINTS = {
    "GCD_LCM_GEAR": ["톱니 수와 회전 횟수의 관계를 연결해 같은 톱니가 만나는 조건을 찾는다.", "반복되는 회전 상황을 공배수 관계로 바꾸어 해석한다."],
    "GCD_LCM_CYCLE": ["각 사건이 반복되는 주기를 확인한다.", "동시에 발생하는 시점은 주기들의 공배수로 해석한다."],
    "GCD_LCM_PRIME_FACTOR": ["각 수를 소인수의 곱으로 나타내 공통 인수와 전체 인수를 구분한다.", "최대공약수와 최소공배수에 적용되는 소인수의 지수 규칙을 구분한다."],
    "GCD_LCM_DIVISOR": ["각 수의 약수 중 공통으로 나타나는 항목을 확인한다.", "공약수와 공배수를 문제에서 요구하는 방향에 맞게 구분한다."],
    "GCD_LCM_PRODUCT_RELATION": ["두 수의 곱과 최대공약수·최소공배수 사이의 관계를 활용한다.", "구한 값이 해당 관계를 만족하는지 역으로 확인한다."],
    "GCD_LCM_MULTIPLE": ["공약수와 공배수의 의미를 먼저 구분한다.", "공약수는 나누어떨어짐으로, 공배수는 배수에 포함됨으로 확인한다."],
    "PRIME_FACTOR_DIVISOR_COUNT": ["소인수의 종류와 지수를 먼저 확인한다.", "소인수의 지수를 이용해 약수 개수의 규칙을 적용한다."],
    "PRIME_COMPOSITE_CLASSIFY": ["약수가 1과 자기 자신뿐인지 확인해 소수 여부를 판단한다.", "그 외의 약수가 있으면 합성수로 분류한다."],
    "PRIME_FACTOR_DIVISORS": ["소인수분해 결과에서 약수를 만들 수 있는 소인수의 조합을 확인한다.", "같은 약수가 중복되지 않도록 조합을 정리한다."],
    "PRIME_FACTOR_DIRECT": ["나눌 수 있는 소수부터 차례로 확인한다.", "더 이상 소수로 나누어지지 않을 때까지 분해한 결과를 정리한다."],
    "LINEAR_EQUATION_WORD": ["문제의 상황에서 알려진 양과 구하려는 양을 구분한다.", "상황의 관계를 하나의 일차방정식으로 표현한 뒤 조건의 의미를 확인한다."],
    "LINEAR_EQUATION_SOLVE": ["등식의 성질을 이용해 미지수가 포함된 항을 정리한다.", "양변에 같은 연산을 적용해 해를 구하고 원래 식에 대입해 확인한다."],
    "ALGEBRA_SUBSTITUTION": ["문자 대신 주어진 값을 대입할 때 연산 순서와 괄호를 확인한다.", "대입한 식을 항별로 계산해 하나의 값이나 식으로 정리한다."],
    "ALGEBRA_DISTRIBUTION": ["괄호 밖의 항을 괄호 안의 각 항에 빠짐없이 곱한다.", "동류항을 구분하여 전개한 식을 정리한다."],
    "ALGEBRA_SIMPLIFY": ["동류항과 연산의 우선순위를 구분한다.", "괄호와 부호를 정리한 뒤 같은 종류의 항을 계산한다."],
    "ALGEBRA_EXPRESSION": ["문자와 수의 관계를 식으로 표현하는 방법을 확인한다.", "식의 각 항과 계수의 의미를 구분해 읽는다."],
    "RATIONAL_SIGN": ["곱셈과 나눗셈에서 부호의 규칙을 먼저 확인한다.", "계산 결과의 부호와 절댓값을 나누어 판단한다."],
    "RATIONAL_FRACTION": ["분수의 곱셈과 나눗셈 규칙을 적용한다.", "나눗셈을 역수의 곱셈으로 바꾸고 약분 가능성을 확인한다."],
    "RATIONAL_OPERATION": ["계산할 수의 부호와 연산 순서를 확인한다.", "정수와 유리수의 계산 규칙에 따라 결과를 정리한다."],
    "RATIONAL_COMPARE": ["수직선에서 오른쪽에 있는 수가 더 큰 수임을 기준으로 비교한다.", "음수는 절댓값이 클수록 작아지는 점을 확인해 순서를 정한다."],
    "RATIONAL_COMPARE_ABS": ["부호와 절댓값을 나누어 각 수의 위치를 확인한다.", "음수의 대소를 비교할 때 절댓값의 크기와 수직선의 방향을 함께 본다."],
    "RATIONAL_ADD_SUB": ["덧셈과 뺄셈을 부호와 절댓값의 관계로 바꾸어 본다.", "같은 부호는 절댓값을 더하고, 다른 부호는 절댓값의 차를 구해 부호를 결정한다."],
    "RATIONAL_ADD_SUB_ABS": ["절댓값은 수직선에서 원점까지의 거리로 해석한다.", "부호에 따른 덧셈·뺄셈 규칙을 적용한 뒤 결과의 위치를 확인한다."],
    "DATA_RELATIVE_FREQUENCY": ["상대도수는 전체 도수에 대한 각 계급의 비율이다.", "도수와 상대도수 사이의 관계를 이용해 부족한 값을 구한다."],
    "DATA_CENTER": ["자료의 개수와 순서를 먼저 확인한다.", "평균·중앙값·최빈값의 정의에 맞는 자료의 위치와 빈도를 찾는다."],
    "DATA_STEM_LEAF": ["줄기와 잎의 자릿값을 확인해 원자료를 읽는다.", "자료를 크기순으로 해석해 필요한 대푯값이나 범위를 찾는다."],
    "DATA_TABLE_READ": ["표의 행과 열이 나타내는 기준을 확인한다.", "질문에 해당하는 계급과 도수를 찾아 자료의 분포를 해석한다."],
    "GEOMETRY_PARALLEL": ["평행선에서 생기는 각의 관계와 위치를 확인한다.", "동위각·엇각·맞꼭지각 등 관련 각의 성질을 연결한다."],
    "GEOMETRY_ANGLE": ["각의 위치 관계와 기본 각의 성질을 확인한다.", "주어진 각과 서로 같거나 합이 정해진 각을 연결한다."],
    "GEOMETRY_RELATION": ["점·선·면의 위치 관계를 그림과 조건으로 구분한다.", "정의와 성질에 맞는 관계를 선택해 판단한다."],
    "PLANE_CONGRUENCE": ["두 도형에서 대응하는 변과 각을 찾아 비교한다.", "SSS·SAS·ASA 등 합동 조건을 만족하는지 확인한다."],
    "PLANE_POLYGON_ANGLE": ["다각형의 변 개수와 내각·외각의 관계를 확인한다.", "전체 각의 합 또는 한 각의 크기를 조건에 맞게 적용한다."],
    "PLANE_AREA": ["도형을 익숙한 기본 도형으로 나누거나 합친다.", "각 부분의 넓이를 구한 뒤 전체 관계에 맞게 계산한다."],
    "PLANE_PROPERTY": ["도형의 정의와 주어진 성질을 구분해 확인한다.", "조건에 맞는 변·각·대칭 관계를 그림과 연결한다."],
    "SOLID_MEASURE": ["입체도형을 밑면과 높이 등 필요한 요소로 나누어 본다.", "겉넓이와 부피의 공식에서 각 요소가 의미하는 값을 확인한다."],
    "SOLID_SECTION": ["회전축이나 절단면의 위치가 만들어 내는 도형을 확인한다.", "입체도형의 구조와 단면의 기본 도형을 연결한다."],
    "SOLID_PROPERTY": ["면·모서리·꼭짓점의 관계를 입체도형의 정의와 연결한다.", "주어진 조건에 맞는 입체도형의 성질을 확인한다."],
    "COORDINATE_POSITION": ["좌표의 순서와 부호를 확인해 점의 위치를 판단한다.", "x축·y축과 사분면의 조건에 맞게 좌표를 해석한다."],
    "COORDINATE_GRAPH_READ": ["가로축과 세로축이 나타내는 양을 먼저 확인한다.", "그래프의 점이나 변화 방향을 읽어 두 양의 관계를 해석한다."],
    "COORDINATE_POINT": ["순서쌍의 첫째 값과 둘째 값이 나타내는 위치를 구분한다.", "좌표평면에서 기준축과 점의 위치를 연결한다."],
    "PROPORTIONAL_APPLICATION": ["두 양 사이에서 변하는 양과 일정하게 유지되는 조건을 구분한다.", "주어진 상황을 두 양의 관계식으로 표현하고 필요한 값을 찾는다."],
    "PROPORTIONAL_GRAPH": ["표현된 그래프의 모양과 원점을 지나는지 확인한다.", "그래프의 일정한 변화 관계가 정비례인지 반비례인지 판단한다."],
    "PROPORTIONAL_FORMULA": ["두 양의 대응값을 비교해 일정한 관계가 있는지 확인한다.", "관계식을 세워 한 양이 변할 때 다른 양이 어떻게 변하는지 해석한다."],
    "PROPORTIONAL_RELATION": ["두 양의 변화가 함께 커지는지 반대로 변하는지 비교한다.", "표·식·그래프 중 문제에 주어진 표현으로 비례 관계를 확인한다."],
}


CONCEPT_SUMMARIES = {
    "최대공약수와 최소공배수": "여러 수의 공약수와 공배수의 의미를 이해하고 문제 상황에 맞는 값을 찾는 방법을 익힌다.",
    "소인수 분해": "합성수를 소수인 인수의 곱으로 나타내고 그 결과를 여러 문제에 활용하는 방법을 이해한다.",
    "문자의 사용과 식의 계산": "문자와 수를 사용해 관계를 식으로 나타내고 식의 의미와 계산 방법을 이해한다.",
    "일차방정식": "등식의 성질을 이용해 일차방정식을 풀고 문제 상황에 적용하는 방법을 이해한다.",
    "정수, 유리수의 곱셈/나눗셈": "정수와 유리수의 곱셈·나눗셈에서 부호와 계산 규칙을 이해한다.",
    "정수, 유리수의 덧셈/뺄셈": "정수와 유리수의 덧셈·뺄셈에서 부호와 절댓값의 관계를 이해한다.",
    "도수분포표와 상대도수": "자료를 표와 그래프로 정리하고 도수와 상대도수로 분포를 해석하는 방법을 이해한다.",
    "기본 도형": "점·선·면·각의 기본 성질과 도형 사이의 위치 관계를 이해한다.",
    "평면도형의 성질": "평면도형의 변과 각의 관계, 합동과 넓이 등 주요 성질을 이해한다.",
    "입체도형의 성질": "입체도형의 구성 요소와 겉넓이·부피·단면의 성질을 이해한다.",
    "좌표평면과 그래프": "좌표평면에서 점의 위치를 나타내고 그래프로 두 양의 관계와 변화를 해석한다.",
    "정비례와 반비례": "두 양의 변화 관계를 표·식·그래프로 나타내고 정비례와 반비례를 구분한다.",
    "유리수의 대소 관계": "수직선과 절댓값을 이용해 유리수의 크기와 순서를 비교한다.",
}


def build_learning_guide(question: dict[str, Any], source28_references: list[dict[str, Any]] | None = None) -> dict[str, Any]:
    metadata = question.get("sourceMetadata") or {}
    title = metadata.get("curriculumSmallUnitName") or next(
        (str(m.get("curriculumExternalKey", "")).split(">")[-1] for m in question.get("curriculumMappings") or [] if m.get("curriculumExternalKey")),
        "중1 수학 핵심 개념",
    )
    answers = [str(x.get("raw") or "") for x in (question.get("answerSpec") or {}).get("finalAnswer", []) if isinstance(x, dict)]
    summary = CONCEPT_SUMMARIES.get(title, f"{title}의 핵심 개념과 주요 성질을 이해하고 문제에 적용하는 방법을 익힌다.")
    strategy = classify_learning_strategy(question)
    points = STRATEGY_POINTS.get(strategy, [summary])[:3]
    summary = _without_answer_leakage(summary, answers)
    points = [_without_answer_leakage(point, answers) for point in points]
    references = sorted(source28_references or [], key=lambda row: str(row.get("sourceRef") or ""))[:3]
    source_refs = [str(row["sourceRef"]) for row in references if row.get("sourceRef")] or [question.get("sourceRef")]
    datasets = [f"AIHUB_{str(question.get('sourceRef', '')).partition(':')[0]}"]
    if references:
        datasets.append("AIHUB_28")
    return {
        "conceptTitle": title, "summary": summary, "keyPoints": list(dict.fromkeys(x for x in points if x)),
        "questionSourceRef": question.get("sourceRef"),
        "source": {
            "achievementStandardCodes": metadata.get("achievementStandardCodes2022") or [],
            "datasets": list(dict.fromkeys(dataset for dataset in datasets if dataset != "AIHUB_")),
            "generationMethod": "DETERMINISTIC_SOURCE_ASSISTED", "sourceRefs": source_refs,
        },
        "status": "SOURCE_GROUNDED",
    }
