package com.cenedu.backend.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** 대시보드 학기 누적 요약과 학생 상태별 인원수. */
public record DashboardSummaryResponse(
        OffsetDateTime calculatedAt,
        LearningSummary summary,
        StudentStatusCounts studentStatusCounts
) {
    public record LearningSummary(
            int assignmentCount,
            int inProgressAssignmentCount,
            BigDecimal classAccuracyRate,
            int aggregatedStudentCount,
            int incompleteSubmissionCount,
            int overdueSubmissionCount,
            int weaknessStudentCount,
            BigDecimal weaknessThresholdRate
    ) {
    }

    public record StudentStatusCounts(
            int delayed,
            int needsSupport,
            int good,
            int insufficientData
    ) {
    }
}
