package com.cenedu.backend.domain.problem.authoring.generation;

/** topicCode에 의존하지 않고 문제 생성과 검증에 사용할 교육과정 범위를 전달한다. */
public record CurriculumContext(
        Long subUnitId,
        Integer grade,
        Integer semester,
        String majorUnitName,
        String middleUnitName,
        String subUnitName
) {
}
