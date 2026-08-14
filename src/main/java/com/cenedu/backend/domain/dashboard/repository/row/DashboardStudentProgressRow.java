package com.cenedu.backend.domain.dashboard.repository.row;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.global.common.enums.AssignmentStatus;

/** 학생과 학습지 한 칸의 진행·성취 조회값. */
public record DashboardStudentProgressRow(
        Long studentId,
        String studentName,
        Long assignmentId,
        AssignmentStatus assignmentStatus,
        int progressCount,
        OffsetDateTime dueAt,
        int gradedItemCount,
        int correctItemCount,
        BigDecimal totalScore,
        OffsetDateTime latestLearningAt
) {
}
