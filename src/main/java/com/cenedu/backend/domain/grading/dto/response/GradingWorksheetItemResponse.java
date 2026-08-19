package com.cenedu.backend.domain.grading.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 평가 결과 목록의 학습지 한 줄.
 *
 * @param pendingCount 제출했는데 아직 {@code GRADED}가 아닌 학생 수. <b>미제출자는 세지 않는다</b> —
 *                     넣으면 결석생 한 명 때문에 확정이 영원히 막힌다(명세 4절)
 */
public record GradingWorksheetItemResponse(
        Long assignmentId,
        Long worksheetId,
        String title,

        @Schema(allowableValues = {"practice", "assessment"})
        String type,

        @Schema(allowableValues = {"standard", "custom"})
        String origin,

        String className,

        @Schema(description = "서버 파생값(명세 2.4)", allowableValues = {"grading", "graded", "confirmed"})
        String status,

        @Schema(description = "확정 후 정정됨")
        boolean modified,

        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,
        int studentCount,
        int submittedCount,
        int gradedCount,
        int pendingCount,

        @Schema(description = "이 배정에서 파생된 맞춤 학습. 맞춤이 없으면 null. "
                + "맞춤 배정은 최상위 목록에 형제로 나오지 않고 여기에만 들어온다", nullable = true)
        GradingCustomLearningResponse customLearning
) {

    public static GradingWorksheetItemResponse of(
            WorksheetAssignment assignment, String className, String status, boolean modified,
            int studentCount, int submittedCount, int gradedCount, int pendingCount,
            GradingCustomLearningResponse customLearning
    ) {
        return new GradingWorksheetItemResponse(
                assignment.getId(),
                assignment.getWorksheet().getId(),
                assignment.getWorksheet().getTitle(),
                GradingResponseFormatter.toApiType(assignment.getWorksheet().getType()),
                GradingResponseFormatter.toApiOrigin(assignment.getWorksheet().getOrigin()),
                className,
                status,
                modified,
                assignment.getAssignedAt(),
                assignment.getDueAt(),
                studentCount,
                submittedCount,
                gradedCount,
                pendingCount,
                customLearning);
    }
}
