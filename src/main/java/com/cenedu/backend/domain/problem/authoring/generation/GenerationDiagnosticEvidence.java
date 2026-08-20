package com.cenedu.backend.domain.problem.authoring.generation;

import java.math.BigDecimal;

import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;

/** 풀이 단계별 채점 표본과 오답 분포다. */
public record GenerationDiagnosticEvidence(
        DiagnosticType diagnosticType,
        int gradedUnitCount,
        int incorrectUnitCount,
        BigDecimal incorrectRate
) {
}
