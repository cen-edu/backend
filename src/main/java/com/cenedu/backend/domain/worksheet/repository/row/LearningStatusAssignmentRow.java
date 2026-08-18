package com.cenedu.backend.domain.worksheet.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 학습 현황 목록 한 줄. {@code sourceAssignmentId}는 배포 ID다(학습지 ID가 아니다) —
 * {@code Worksheet.sourceAssignment}가 {@code LAZY}라 프록시를 건드리지 않게 여기서 ID만 뽑는다.
 */
public record LearningStatusAssignmentRow(
        Long assignmentId,
        Long worksheetId,
        String title,
        WorksheetType type,
        WorksheetOrigin origin,
        Long classId,
        short grade,
        String semester,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        Long sourceAssignmentId
) {
}
