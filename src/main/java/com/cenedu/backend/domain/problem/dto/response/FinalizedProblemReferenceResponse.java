package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.global.common.enums.QuestionType;

/** Worksheet가 최종화된 문제를 참조할 때 사용하는 최소 공개 계약이다. */
public record FinalizedProblemReferenceResponse(
        Long sessionId,
        Long versionId,
        Long questionId,
        QuestionType questionType
) {
}
