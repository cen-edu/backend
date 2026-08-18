package com.cenedu.backend.domain.problem.authoring.edit;

/** 수정 대상을 영역과 S1 논리 키의 쌍으로 표현한다. 단일 영역은 targetKey가 null이다. */
public record ProblemEditTargetRef(
        EditTargetType targetType,
        String targetKey
) {
}
