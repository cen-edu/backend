package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;

import io.swagger.v3.oas.annotations.media.Schema;

/** 선택한 배포의 학생별 진행 표. */
public record LearningStatusStudentsResponse(
        Long assignmentId,
        String title,

        @Schema(description = "학습지 유형", allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "반 이름. 개별 학생 배포(맞춤 학습)는 null", nullable = true)
        String className,

        OffsetDateTime dueAt,
        int totalUnits,
        List<LearningStatusStudentResponse> students
) {

    public static LearningStatusStudentsResponse from(
            WorksheetAssignment assignment, String className, int totalUnits,
            List<LearningStatusStudentResponse> students
    ) {
        Worksheet worksheet = assignment.getWorksheet();
        return new LearningStatusStudentsResponse(
                assignment.getId(),
                worksheet.getTitle(),
                WorksheetResponseFormatter.toApiType(worksheet.getType()),
                className,
                assignment.getDueAt(),
                totalUnits,
                List.copyOf(students));
    }
}
