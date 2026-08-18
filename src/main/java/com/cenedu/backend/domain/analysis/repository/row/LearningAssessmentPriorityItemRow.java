package com.cenedu.backend.domain.analysis.repository.row;

/** 학습평가에서 정답률이 낮아 우선 확인해야 하는 문항 집계 행. */
public record LearningAssessmentPriorityItemRow(
        Long worksheetItemId,
        int itemNumber,
        String questionTitle,
        String evaluationArea,
        int sourceDifficulty,
        int correctStudentCount,
        int gradedStudentCount
) {
}
