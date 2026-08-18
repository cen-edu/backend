package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학습평가 학생과 학급의 평가 영역 또는 난이도별 완전정답률 집계 행. */
public record StudentLearningGroupComparisonRow(
        GroupDimension dimension,
        String groupCode,
        int itemCount,
        int studentGradedResultCount,
        BigDecimal studentAccuracyRate,
        int classGradedResultCount,
        BigDecimal classAccuracyRate
) {
    public enum GroupDimension {
        EVALUATION_AREA,
        DIFFICULTY
    }
}
