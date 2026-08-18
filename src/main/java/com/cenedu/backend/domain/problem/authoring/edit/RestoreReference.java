package com.cenedu.backend.domain.problem.authoring.edit;

/** 복원할 Version을 자연어 지시와 분리한 구조화된 참조로 표현한다. */
public record RestoreReference(
        RestoreReferenceType type,
        Integer versionNo
) {
}
