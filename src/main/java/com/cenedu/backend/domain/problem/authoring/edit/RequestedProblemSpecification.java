package com.cenedu.backend.domain.problem.authoring.edit;

import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 교사가 문항 자체를 다른 문항으로 바꿔달라고 했을 때의 교체 조건이다.
 *
 * <p>{@code questionType}·{@code difficulty}는 바꿀 값이 있을 때만 채우고, 나머지는 null로 둔다.
 * {@code requiresAsset}은 "이미지가 있는 문제로 바꿔줘"처럼 자료 유무 자체가 조건인 요청을 담으며
 * null이면 무관을 뜻한다. {@code differentProblemOnly}는 조건은 그대로 두고 다른 문항을 원하는
 * 요청("같은 조건으로 다른 문제 줘")을 표현한다 — 이 값이 없으면 그런 요청은 바꿀 값이 하나도 없어
 * {@link #isEmpty()}에 걸려 교체 요청이 아닌 것으로 사라진다.
 *
 * <p>{@code requiresNewProblem}은 교사가 기존 문항이 아니라 새로 만든 문항을 원한다고 명시한
 * 경우다("새로 만들어줘", "직접 출제해줘"). 교체는 기본적으로 문제은행 조회를 먼저 시도하므로,
 * 이 신호가 없으면 그런 요청도 이미 적재된 문항으로 조용히 대체된다.
 */
public record RequestedProblemSpecification(
        QuestionType questionType,
        String difficulty,
        Boolean requiresAsset,
        boolean differentProblemOnly,
        boolean requiresNewProblem
) {
    public RequestedProblemSpecification {
        if (difficulty != null && !java.util.Set.of("low", "mid", "high").contains(difficulty)) {
            throw new IllegalArgumentException("수정 요청 난이도는 low, mid, high 중 하나여야 합니다.");
        }
    }

    public RequestedProblemSpecification(QuestionType questionType, String difficulty) {
        this(questionType, difficulty, null, false, false);
    }

    public RequestedProblemSpecification(QuestionType questionType, String difficulty,
            Boolean requiresAsset, boolean differentProblemOnly) {
        this(questionType, difficulty, requiresAsset, differentProblemOnly, false);
    }

    /**
     * 바꿀 값이 하나도 없는, 즉 요청이 없는 것과 같은 스펙인지 알린다.
     *
     * <p>구조화 출력 스키마가 모든 필드를 required로 두기 때문에 모델은 변경 요청이 없을 때
     * {@code null} 대신 값이 모두 비어 있는 객체를 자주 반환한다. 예전에는 생성자가 여기서 예외를
     * 던져 수정 턴 전체가 500으로 끝났다. 이제는 값으로 받아들이고 {@code ProblemEditAgent}가
     * Agent 경계에서 null로 정규화한다 — 이후 코드가 모두 {@code requestedSpecification != null}로
     * "교체 요청인가"를 판단하므로, 빈 스펙이 그대로 흘러가면 아무 변경도 없는 문항 전체
     * 재생성이 일어난다.
     */
    public boolean isEmpty() {
        return questionType == null && difficulty == null
                && requiresAsset == null && !differentProblemOnly && !requiresNewProblem;
    }
}
