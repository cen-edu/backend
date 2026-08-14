package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 문항 유형 또는 난이도 구간별 문항 수와 완전정답률 집계 행. */
public record AssessmentGroupAggregateRow(
        GroupDimension dimension,
        String groupCode,
        int itemCount,
        int gradedResultCount,
        BigDecimal accuracyRate
) {
    public enum GroupDimension {
        QUESTION_TYPE,
        DIFFICULTY
    }
}
