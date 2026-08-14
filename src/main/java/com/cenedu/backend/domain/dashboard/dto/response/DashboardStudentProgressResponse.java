package com.cenedu.backend.domain.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardStudentStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 대시보드 학습지 열과 학생별 누적 학습 현황. */
public record DashboardStudentProgressResponse(
        List<WorksheetColumn> worksheetColumns,
        List<StudentProgress> students
) {
    public DashboardStudentProgressResponse {
        worksheetColumns = List.copyOf(worksheetColumns);
        students = List.copyOf(students);
    }

    public record WorksheetColumn(
            Long assignmentId,
            String worksheetTitle,
            WorksheetType worksheetType,
            WorksheetOrigin worksheetOrigin
    ) {
    }

    public record StudentProgress(
            Long studentId,
            String studentName,
            DashboardStudentStatus status,
            int completedAssignmentCount,
            int totalAssignmentCount,
            BigDecimal averageAccuracyRate,
            OffsetDateTime latestLearningAt,
            List<WorksheetResult> worksheetResults
    ) {
        public StudentProgress {
            worksheetResults = List.copyOf(worksheetResults);
        }
    }

    public record WorksheetResult(
            Long assignmentId,
            AssignmentProgressStatus status,
            BigDecimal resultValue
    ) {
    }
}
