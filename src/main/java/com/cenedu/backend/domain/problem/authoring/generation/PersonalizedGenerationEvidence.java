package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;

/** 개인별 맞춤 문제 생성에 사용하는 구조화된 취약점 관찰 근거다. */
public record PersonalizedGenerationEvidence(
        int historicalIncorrectItemCount,
        int incorrectSessionCount,
        List<GenerationEvaluationAreaEvidence> evaluationAreaEvidence,
        List<GenerationDiagnosticEvidence> diagnosticEvidence
) {
    public PersonalizedGenerationEvidence {
        evaluationAreaEvidence = evaluationAreaEvidence == null ? List.of() : List.copyOf(evaluationAreaEvidence);
        diagnosticEvidence = diagnosticEvidence == null ? List.of() : List.copyOf(diagnosticEvidence);
    }
}
