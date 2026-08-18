package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.global.common.enums.EvaluationArea;

/** 학습평가 학생 상세의 평가 영역·난이도별 학생과 학급 성취 비교. */
public record StudentLearningAssessmentPerformanceResponse(
        List<EvaluationAreaComparison> evaluationAreas,
        List<DifficultyBandComparison> difficultyBands,
        List<StudentSubcategoryResult> subcategoryResults
) {
    public StudentLearningAssessmentPerformanceResponse {
        evaluationAreas = List.copyOf(evaluationAreas);
        difficultyBands = List.copyOf(difficultyBands);
        subcategoryResults = List.copyOf(subcategoryResults);
    }

    public record EvaluationAreaComparison(
            EvaluationArea evaluationArea,
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

    public record StudentSubcategoryResult(
            Long subcategoryId,
            String subcategoryName,
            int correctCount,
            int gradedCount
    ) {
    }
}
