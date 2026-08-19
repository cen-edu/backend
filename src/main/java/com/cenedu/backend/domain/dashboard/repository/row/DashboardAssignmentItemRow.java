package com.cenedu.backend.domain.dashboard.repository.row;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 대시보드 학습지 목록 한 행의 배정·제출·채점 조회값.
 *
 * @param sourceAssignmentId    맞춤 학습지가 어느 배정에서 파생됐는지. 맞춤이 아니면 {@code null}
 * @param releasedStudentCount  결과가 공개된 학생 수. 확정은 제출자에게만 찍힌다
 */
public record DashboardAssignmentItemRow(
        Long assignmentId,
        String worksheetTitle,
        WorksheetType worksheetType,
        WorksheetOrigin worksheetOrigin,
        Long sourceAssignmentId,
        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        int studentCount,
        int submittedStudentCount,
        int gradedStudentCount,
        int releasedStudentCount
) {
}
