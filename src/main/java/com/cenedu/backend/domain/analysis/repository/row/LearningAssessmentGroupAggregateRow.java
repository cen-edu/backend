package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 학습평가의 평가 영역 또는 난이도 구간별 완전정답률 집계 행. */
public record LearningAssessmentGroupAggregateRow(
        GroupDimension dimension,
        String groupCode,
        int itemCount,
        int gradedResultCount,
        BigDecimal accuracyRate
) {
    public enum GroupDimension {
        EVALUATION_AREA,
        DIFFICULTY
    }
}
