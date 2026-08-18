package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학생 수행 요약과 같은 학습지의 학급 성취 집계 행. */
public record StudentAnalysisSummaryRow(
        String studentName,
        int totalItemCount,
        int gradedItemCount,
        int correctItemCount,
        BigDecimal accuracyRate,
        BigDecimal classAccuracyRate,
        BigDecimal scoreRate,
        BigDecimal classScoreRate,
        Long totalSolvingDurationMs,
        Long classAverageSolvingDurationMs
) {
}
