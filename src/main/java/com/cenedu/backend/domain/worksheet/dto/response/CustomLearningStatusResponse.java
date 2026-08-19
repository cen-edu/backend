package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignment;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 원본 배정 하나에 딸린 맞춤 학습 현황.
 *
 * <p>연관은 학습지가 아니라 <b>배정</b>으로 건다({@code worksheet.source_assignment_id}). 같은
 * 학습지를 두 반에 배포해도 반별로 갈린다.
 */
public record CustomLearningStatusResponse(

        Long sourceAssignmentId,
        Long sourceWorksheetId,
        String sourceTitle,

        @Schema(nullable = true)
        Long classId,

        @Schema(nullable = true)
        String className,

        CustomLearningSummaryResponse summary,

        @Schema(description = "맞춤 학습지 목록. 회차 오름차순")
        List<CustomLearningWorksheetResponse> worksheets
) {

    public static CustomLearningStatusResponse of(
            WorksheetAssignment sourceAssignment, String className,
            List<CustomLearningWorksheetResponse> worksheets
    ) {
        return new CustomLearningStatusResponse(
                sourceAssignment.getId(),
                sourceAssignment.getWorksheet().getId(),
                sourceAssignment.getWorksheet().getTitle(),
                sourceAssignment.getClassId(),
                className,
                CustomLearningSummaryResponse.of(worksheets),
                List.copyOf(worksheets));
    }
}
