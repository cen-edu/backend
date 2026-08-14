package com.cenedu.backend.domain.dashboard.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 대시보드 학생 현황 표의 학습지 열 조회값. */
public record DashboardWorksheetColumnRow(
        Long assignmentId,
        String worksheetTitle,
        WorksheetType worksheetType,
        WorksheetOrigin worksheetOrigin,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt
) {
}
