package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학생이 정답률 60% 미만을 기록한 소분류 집계 행. */
public record StudentWeakSubcategoryRow(
        Long subcategoryId,
        String subcategoryName,
        int incorrectCount,
        int gradedCount,
        BigDecimal accuracyRate
) {
}
