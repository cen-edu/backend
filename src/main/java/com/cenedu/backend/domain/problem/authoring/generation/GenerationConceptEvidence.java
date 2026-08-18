package com.cenedu.backend.domain.problem.authoring.generation;

/** 직접 매핑된 개념이 있을 때만 선택적으로 생성 근거를 보강한다. */
public record GenerationConceptEvidence(
        Long conceptId,
        String conceptName,
        String evidence
) {
}
