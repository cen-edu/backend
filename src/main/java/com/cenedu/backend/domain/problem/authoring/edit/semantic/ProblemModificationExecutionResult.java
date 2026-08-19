package com.cenedu.backend.domain.problem.authoring.edit.semantic;

/** 의미 수정 실행 결과와 검증·승격 상태를 함께 반환한다. */
public record ProblemModificationExecutionResult(Long previewVersionId,
        SemanticEditMode mode, ProblemSemanticDiff diff,
        boolean promoted, boolean legacyFallback) {
}
