package com.cenedu.backend.domain.worksheet.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusAssignmentRow;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학습 현황 목록의 배포 한 줄.
 *
 * <p>{@code status}는 컬럼이 아니라 {@code due_at} 경과로 파생한다. 채점 진행은 보지 않는다 —
 * 그 축은 채점 화면이 담당한다(명세 8-①은 팀 확인 대기).
 */
public record LearningStatusAssignmentResponse(
        Long assignmentId,
        Long worksheetId,
        String title,

        @Schema(description = "학습지 유형", allowableValues = {"practice", "assessment"})
        String type,

        @Schema(description = "출제 방식", allowableValues = {"manual", "custom"})
        String origin,

        Long classId,

        @Schema(description = "반 이름. 개별 학생 배포(맞춤 학습)는 null", nullable = true)
        String className,

        short grade,

        @Schema(description = "학기", allowableValues = {"first", "second", "common"})
        String semester,

        @Schema(description = "학습지 진행 상태", allowableValues = {"ongoing", "completed"})
        String status,

        OffsetDateTime assignedAt,
        OffsetDateTime dueAt,

        @Schema(description = "진행률 분모. 종합평가는 문항 수, 일반·맞춤 학습은 채점 칸 수 합")
        int totalUnits,

        int studentCount,
        long submittedCount,

        @Schema(description = "맞춤 학습지를 만들어 낸 배포 ID. 학습지 ID가 아니다", nullable = true)
        Long sourceAssignmentId
) {

    public static LearningStatusAssignmentResponse from(
            LearningStatusAssignmentRow row,
            String className,
            String status,
            int totalUnits,
            int studentCount,
            long submittedCount
    ) {
        return new LearningStatusAssignmentResponse(
                row.assignmentId(),
                row.worksheetId(),
                row.title(),
                WorksheetResponseFormatter.toApiType(row.type()),
                WorksheetResponseFormatter.toApiOrigin(row.origin()),
                row.classId(),
                className,
                row.grade(),
                WorksheetResponseFormatter.toApiSemester(row.semester()),
                status,
                row.assignedAt(),
                row.dueAt(),
                totalUnits,
                studentCount,
                submittedCount,
                row.sourceAssignmentId()
        );
    }
}
