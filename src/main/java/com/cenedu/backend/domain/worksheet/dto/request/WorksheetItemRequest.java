package com.cenedu.backend.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** 학습지에 담길 문항 한 줄. */
public record WorksheetItemRequest(
        @NotNull(message = "questionId는 필수입니다.")
        Long questionId,

        @NotNull(message = "displayOrder는 필수입니다.")
        Integer displayOrder,

        @Schema(allowableValues = {"concept", "chat"})
        String supportMode,

        @Schema(allowableValues = {"retrace", "basic", "independent"})
        String customStage
) {
}
