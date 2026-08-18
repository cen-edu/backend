package com.cenedu.backend.domain.problem.authoring.edit;

/** 교사 자연어에서 추출한 수정 대상, 영향 성격, 수행 지시다. */
public record ProblemEditInstruction(
        EditTargetType targetType,
        String targetKey,
        EditChangeNature changeNature,
        String instruction
) {
}
