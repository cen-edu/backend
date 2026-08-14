package com.cenedu.backend.domain.analysis.repository.row;

/** 종합평가에서 정답률이 낮아 우선 확인해야 하는 문항 집계 행. */
public record AssessmentPriorityItemRow(
        Long worksheetItemId,
        int itemNumber,
        String questionTitle,
        int sourceDifficulty,
        int correctStudentCount,
        int gradedStudentCount
) {
}
