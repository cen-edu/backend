package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.Worksheet;
import com.cenedu.backend.domain.worksheet.entity.WorksheetAssignmentStudent;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 결과. {@code released_at IS NULL}이면 이 응답 자체를 만들지 않는다(게이트가 서비스에서
 * 먼저 막는다) — 점수·정답·해설·루브릭 어느 것도 새지 않는다.
 */
public record StudentResultResponse(
        Long assignmentStudentId,
        String title,

        @Schema(description = "학습지 유형", allowableValues = {"practice", "assessment"})
        String type,

        BigDecimal totalScore,
        BigDecimal maxTotalScore,
        OffsetDateTime gradedAt,
        List<StudentResultItemResponse> items
) {

    public static StudentResultResponse from(
            WorksheetAssignmentStudent was,
            List<StudentResultItemResponse> items,
            BigDecimal totalScore,
            BigDecimal maxTotalScore
    ) {
        Worksheet worksheet = was.getAssignment().getWorksheet();
        return new StudentResultResponse(
                was.getId(),
                worksheet.getTitle(),
                WorksheetResponseFormatter.toApiType(worksheet.getType()),
                totalScore,
                maxTotalScore,
                was.getGradedAt(),
                List.copyOf(items)
        );
    }
}
