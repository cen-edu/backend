package com.cenedu.backend.domain.analysis.repository.row;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 학습지 배정 접근 권한과 화면 문맥을 함께 조회한 행. */
public record AnalysisAssignmentAccessRow(
        Long assignmentId,
        String worksheetTitle,
        WorksheetType worksheetType,
        String className,
        Long worksheetOwnerTeacherId,
        Long homeroomTeacherId
) {
}
