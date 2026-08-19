package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;

/** 종합평가의 문항 유형·난이도별 결과와 우선 확인 문항. */
public record ComprehensiveAssessmentInsightsResponse(
        List<QuestionTypeGroupResult> questionTypeGroups,
        List<DifficultyBandResult> difficultyBands,
        List<ComprehensiveAssessmentPriorityItem> priorityItems
) {
    public ComprehensiveAssessmentInsightsResponse {
        questionTypeGroups = List.copyOf(questionTypeGroups);
        difficultyBands = List.copyOf(difficultyBands);
        priorityItems = List.copyOf(priorityItems);
    }

    public record QuestionTypeGroupResult(
            AssessmentQuestionTypeGroup questionTypeGroup,
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

    /**
     * @param questionTypeGroup 객관식·주관식·서술형. 종합평가 문항에는 평가 영역이 없어서
     *                          문항을 분류해 보여줄 축이 유형뿐이다
     */
    public record ComprehensiveAssessmentPriorityItem(
            Long worksheetItemId,
            int itemNumber,
            String questionTitle,
            AssessmentQuestionTypeGroup questionTypeGroup,
            DifficultyBand difficultyBand,
            int correctStudentCount,
            int gradedStudentCount
    ) {
    }
}
