package com.cenedu.backend.domain.analysis.repository.row;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 분석 학습지 선택 목록 SQL의 한 행. */
public record AnalysisAssignmentRow(
        Long assignmentId,
        String worksheetTitle,
        WorksheetType worksheetType,
        boolean analysisAvailable
) {
}
