package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;

/** 종합평가 학생 상세의 문항 유형·난이도별 학생과 학급 성취 비교. */
public record StudentComprehensiveAssessmentPerformanceResponse(
        List<QuestionTypeGroupComparison> questionTypeGroups,
        List<DifficultyBandComparison> difficultyBands
) {
    public StudentComprehensiveAssessmentPerformanceResponse {
        questionTypeGroups = List.copyOf(questionTypeGroups);
        difficultyBands = List.copyOf(difficultyBands);
    }

    public record QuestionTypeGroupComparison(
            AssessmentQuestionTypeGroup questionTypeGroup,
            int itemCount,
            BigDecimal studentAccuracyRate,
            BigDecimal classAccuracyRate,
            boolean referenceOnly
    ) {
    }

    public record DifficultyBandComparison(
            DifficultyBand difficultyBand,
            int itemCount,
            BigDecimal studentAccuracyRate,
            BigDecimal classAccuracyRate,
            boolean referenceOnly
    ) {
    }
}
