package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

/** 종합평가 학생과 학급의 문항 유형 또는 난이도별 완전정답률 집계 행. */
public record StudentAssessmentGroupComparisonRow(
        GroupDimension dimension,
        String groupCode,
        int itemCount,
        int studentGradedResultCount,
        BigDecimal studentAccuracyRate,
        int classGradedResultCount,
        BigDecimal classAccuracyRate
) {
    public enum GroupDimension {
        QUESTION_TYPE,
        DIFFICULTY
    }
}
