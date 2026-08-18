package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학급 분석 상단 카드에 필요한 집계 결과. */
public record ClassAnalysisOverviewRow(
        int participantCount,
        int gradingPendingStudentCount,
        int gradingPendingAnswerCount,
        BigDecimal classPerformanceRate,
        Long averageSolvingDurationMs,
        int weaknessSubcategoryCount,
        int weaknessStudentCount
) {
}
