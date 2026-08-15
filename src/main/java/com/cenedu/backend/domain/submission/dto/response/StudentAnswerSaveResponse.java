package com.cenedu.backend.domain.submission.dto.response;

import java.time.OffsetDateTime;

import com.cenedu.backend.global.common.enums.AssignmentStatus;

import io.swagger.v3.oas.annotations.media.Schema;

/** 문항 답안 저장 결과. 진행률 계산 주체가 서버라 클라이언트가 따로 세면 어긋난다(명세 6절). */
public record StudentAnswerSaveResponse(
        int doneUnits,
        int totalUnits,

        @Schema(description = "진행 상태",
                allowableValues = {"not-started", "in-progress", "submitted", "not-submitted"})
        String status
) {

    public static StudentAnswerSaveResponse from(
            int doneUnits, int totalUnits, AssignmentStatus status, short progressCount, OffsetDateTime dueAt
    ) {
        return new StudentAnswerSaveResponse(
                doneUnits, totalUnits, SubmissionResponseFormatter.toApiStatus(status, progressCount, dueAt));
    }
}
