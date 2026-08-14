package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학생별 채점 완료 문항 득점률과 측정된 총 풀이시간 집계 행. */
public record ScoreTimeStudentRow(
        Long studentId,
        String studentName,
        int gradedItemCount,
        BigDecimal scoreRate,
        Long totalSolvingDurationMs
) {
}
