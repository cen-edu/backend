package com.cenedu.backend.domain.dashboard.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
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

    public record AssignmentItem(
            Long assignmentId,
            String worksheetTitle,
            WorksheetType worksheetType,
            WorksheetOrigin worksheetOrigin,
            OffsetDateTime assignedAt,
            OffsetDateTime dueAt,
            int studentCount,
            int submittedStudentCount,
            int gradedStudentCount,
            DashboardAssignmentStatus status
    ) {
    }

    public record PageInfo(
            int number,
            long totalElements,
            int totalPages
    ) {
    }
}
