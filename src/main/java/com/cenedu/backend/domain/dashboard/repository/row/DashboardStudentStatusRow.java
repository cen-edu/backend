package com.cenedu.backend.domain.dashboard.repository.row;

import java.math.BigDecimal;

/** 대시보드 학생 상태 분류에 필요한 지연·정답률 조회값. */
public record DashboardStudentStatusRow(
        Long studentId,
        boolean delayed,
        int gradedItemCount,
        BigDecimal accuracyRate
) {
}
