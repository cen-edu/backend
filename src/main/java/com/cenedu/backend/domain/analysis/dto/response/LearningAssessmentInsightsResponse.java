package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.global.common.enums.EvaluationArea;

/** 학습평가의 평가 영역·난이도별 결과와 우선 확인 문항. */
public record LearningAssessmentInsightsResponse(
        List<EvaluationAreaResult> evaluationAreas,
        List<DifficultyBandResult> difficultyBands,
        List<LearningAssessmentPriorityItem> priorityItems
) {
    public LearningAssessmentInsightsResponse {
        evaluationAreas = List.copyOf(evaluationAreas);
        difficultyBands = List.copyOf(difficultyBands);
        priorityItems = List.copyOf(priorityItems);
    }

    public record EvaluationAreaResult(
            EvaluationArea evaluationArea,
            int itemCount,
            BigDecimal accuracyRate,
            boolean referenceOnly
    ) {
    }

    public record DifficultyBandResult(
            DifficultyBand difficultyBand,
            int itemCount,
            BigDecimal accuracyRate,
            boolean referenceOnly
    ) {
    }

    public record LearningAssessmentPriorityItem(
            Long worksheetItemId,
            int itemNumber,
            String questionTitle,
            EvaluationArea evaluationArea,
            DifficultyBand difficultyBand,
            int correctStudentCount,
            int gradedStudentCount
    ) {
    }
}
