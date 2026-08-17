package com.cenedu.backend.domain.problem.authoring.candidate;

import java.util.List;

/** 후보의 생성 경로와 원본·참고 문제 ID를 검증·이력 조회에 전달한다. */
public record CandidateProvenance(
        CandidateSourceType sourceType,
        Long sourceQuestionId,
        List<Long> referenceQuestionIds
) {
}
