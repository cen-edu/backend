package com.cenedu.backend.domain.dashboard.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardResultStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 대시보드 하단의 페이지 단위 학습지 배정 목록. */
public record DashboardAssignmentListResponse(
        List<AssignmentItem> assignments,
        PageInfo page
) {
    public DashboardAssignmentListResponse {
        assignments = List.copyOf(assignments);
    }

    /**
     * @param sourceAssignmentId 맞춤 학습지가 파생된 원본 배정. 맞춤이 아니면 {@code null}
     * @param resultStatus       채점·확정 단계. 제출자가 없으면 {@code null} 이다 — 아직 채점을
     *                           말할 단계가 아니다. {@code status} 와 축이 다르다
     */
    public record AssignmentItem(
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
            DashboardAssignmentStatus status,
            DashboardResultStatus resultStatus
    ) {
    }

    public record PageInfo(
            int number,
            long totalElements,
            int totalPages
    ) {
    }
}
