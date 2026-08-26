package com.cenedu.backend.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 수정 세션의 결과를 학습지 문항에 반영하는 요청. */
public record WorksheetItemEditApplyRequest(
        @Schema(description = "이 문항에서 연 수정 세션 ID")
        @NotNull(message = "sessionId는 필수입니다.")
        Long sessionId
) {
}
