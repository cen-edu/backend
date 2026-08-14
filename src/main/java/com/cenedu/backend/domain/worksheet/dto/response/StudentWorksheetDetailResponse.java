package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;

import io.swagger.v3.oas.annotations.media.Schema;

/** 풀이 진입 화면이 필요한 전부를 한 번에 담는다. */
public record StudentWorksheetDetailResponse(
        Long assignmentStudentId,
        String title,

        @Schema(description = "학습지 유형", allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "출제 방식", allowableValues = {"standard", "custom"})
        String origin,

        @Schema(description = "진행 상태",
                allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String status,

        OffsetDateTime dueAt,
        List<StudentWorksheetItemResponse> items
) {

    public static StudentWorksheetDetailResponse from(
            WorksheetAssignmentStudent was, List<StudentWorksheetItemResponse> items
    ) {
        Worksheet worksheet = was.getAssignment().getWorksheet();
        return new StudentWorksheetDetailResponse(
                was.getId(),
                worksheet.getTitle(),
                WorksheetResponseFormatter.toApiType(worksheet.getType()),
                StudentResponseFormatter.toApiOrigin(worksheet.getOrigin()),
                StudentResponseFormatter.toApiStatus(
                        was.getStatus(), was.getProgressCount(), was.getAssignment().getDueAt()),
                was.getAssignment().getDueAt(),
                List.copyOf(items)
        );
    }
}
