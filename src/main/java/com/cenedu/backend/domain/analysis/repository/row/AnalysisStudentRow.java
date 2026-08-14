package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학생 목록 SQL의 정답률 집계 행. */
public record AnalysisStudentRow(
        Long studentId,
        String studentName,
        int gradedItemCount,
        BigDecimal accuracyRate
) {
}
