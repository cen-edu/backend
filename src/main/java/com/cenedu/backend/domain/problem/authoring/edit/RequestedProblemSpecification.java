package com.cenedu.backend.domain.problem.authoring.edit;

import com.cenedu.backend.global.common.enums.QuestionType;

/** 교사가 문제 유형이나 난이도 변경을 요청했을 때만 설정되는 교체 조건이다. */
public record RequestedProblemSpecification(
        QuestionType questionType,
        String difficulty
) {
}
