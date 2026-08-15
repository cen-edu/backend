package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import com.cenedu.backend.domain.worksheet.repository.row.StudentAssignmentRow;

import io.swagger.v3.oas.annotations.media.Schema;

/** 학생 홈 목록의 배정 한 줄. */
public record StudentAssignmentResponse(
        @Schema(description = "배정 식별자")
        Long assignmentStudentId,

        String title,

        @Schema(description = "학습지 유형", allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "출제 방식", allowableValues = {"standard", "custom"})
        String origin,

        @Schema(description = "홈 화면 탭 분류", allowableValues = {"homework", "assessment"})
        String category,

        short grade,
        String semester,

        @Schema(description = "진행 상태",
                allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String status,

        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        int doneUnits,
        int totalUnits,
        boolean resultReady,
        Long sourceAssignmentStudentId,

        @Schema(description = "맞춤 학습지의 단계 집합. 맞춤이 아니면 null",
                allowableValues = {"retrace", "basic", "independent"})
        List<String> stages
) {

    public static StudentAssignmentResponse from(
            StudentAssignmentRow row, int totalUnits, Long sourceAssignmentStudentId, List<CustomStage> stages
    ) {
        return new StudentAssignmentResponse(
                row.assignmentStudentId(),
                row.title(),
                WorksheetResponseFormatter.toApiType(row.type()),
                StudentResponseFormatter.toApiOrigin(row.origin()),
                StudentResponseFormatter.toApiCategory(row.type()),
                row.grade(),
                row.semester(),
                StudentResponseFormatter.toApiStatus(row.status(), row.progressCount(), row.dueAt()),
                row.assignedAt(),
                row.dueAt(),
                row.progressCount(),
                totalUnits,
                row.releasedAt() != null,
                sourceAssignmentStudentId,
                (stages == null || stages.isEmpty())
                        ? null
                        : stages.stream().map(WorksheetResponseFormatter::toApiCustomStage).toList()
        );
    }
}
