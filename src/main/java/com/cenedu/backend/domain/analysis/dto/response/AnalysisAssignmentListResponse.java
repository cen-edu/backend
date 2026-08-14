package com.cenedu.backend.domain.analysis.dto.response;

import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 분석할 수 있는 학습지 배정 선택 목록. */
public record AnalysisAssignmentListResponse(
        List<AssignmentOption> assignments
) {
    public AnalysisAssignmentListResponse {
        assignments = List.copyOf(assignments);
    }

    public record AssignmentOption(
            Long assignmentId,
            String worksheetTitle,
            WorksheetType worksheetType,
            boolean analysisAvailable
    ) {
    }
}
