package com.cenedu.backend.domain.problem.authoring.edit;

import com.cenedu.backend.global.common.enums.QuestionType;

/** 교사가 문제 유형이나 난이도 변경을 요청했을 때만 설정되는 교체 조건이다. */
public record RequestedProblemSpecification(
        QuestionType questionType,
        String difficulty
) {
    public RequestedProblemSpecification {
        if (difficulty != null && !java.util.Set.of("low", "mid", "high").contains(difficulty)) {
            throw new IllegalArgumentException("수정 요청 난이도는 low, mid, high 중 하나여야 합니다.");
        }
    }

    /**
     * 바꿀 값이 하나도 없는, 즉 요청이 없는 것과 같은 스펙인지 알린다.
     *
     * <p>구조화 출력 스키마가 questionType·difficulty를 둘 다 required로 두기 때문에 모델은
     * 변경 요청이 없을 때 {@code null} 대신 두 값이 모두 null인 객체를 자주 반환한다. 예전에는
     * 생성자가 여기서 예외를 던져 수정 턴 전체가 500으로 끝났다. 이제는 값으로 받아들이고
     * {@code ProblemEditAgent}가 Agent 경계에서 null로 정규화한다 — 이후 코드가 모두
     * {@code requestedSpecification != null}로 "교체 요청인가"를 판단하므로, 빈 스펙이
     * 그대로 흘러가면 아무 변경도 없는 문항 전체 재생성이 일어난다.
     */
    public boolean isEmpty() {
        return questionType == null && difficulty == null;
    }
}
