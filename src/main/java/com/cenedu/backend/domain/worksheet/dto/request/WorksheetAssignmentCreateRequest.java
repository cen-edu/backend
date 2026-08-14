package com.cenedu.backend.domain.worksheet.dto.request;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

/** 학습지를 반에 배포하는 요청. */
public record WorksheetAssignmentCreateRequest(
        @NotNull(message = "classId는 필수입니다.")
        Long classId,

        @NotNull(message = "dueAt은 필수입니다.")
        OffsetDateTime dueAt
) {
}
