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
        if (questionType == null && difficulty == null) {
            throw new IllegalArgumentException("수정할 문항 유형 또는 난이도가 필요합니다.");
        }
    }
}
