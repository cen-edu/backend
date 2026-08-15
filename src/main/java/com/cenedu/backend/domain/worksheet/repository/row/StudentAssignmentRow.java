package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.AssignmentStatus;

/** 학생 홈 목록의 배정 한 줄 projection. */
public record StudentAssignmentRow(
        Long assignmentStudentId,
        Long worksheetId,
        String title,
        WorksheetType type,
        WorksheetOrigin origin,
        short grade,
        String semester,
        AssignmentStatus status,
        short progressCount,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        OffsetDateTime releasedAt,
        Long sourceAssignmentId
) {
}
