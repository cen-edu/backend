package com.cenedu.backend.domain.dashboard.repository.row;

import java.math.BigDecimal;

/** 대시보드 상단 누적 집계 조회값. */
public record DashboardSummaryRow(
        int assignmentCount,
        int inProgressAssignmentCount,
        int customAssignmentCount,
        int customCompletedAssignmentCount,
        BigDecimal classAccuracyRate,
        int aggregatedStudentCount,
        int incompleteSubmissionCount,
        int overdueSubmissionCount,
        int weaknessStudentCount
) {
}
