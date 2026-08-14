package com.cenedu.backend.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 학습지 생성 화면에서 교사가 지정한 출제 조건 한 줄. */
public record WorksheetGenSpecRequest(
        @NotNull(message = "subUnitId는 필수입니다.")
        Long subUnitId,

        @NotBlank(message = "questionType은 필수입니다.")
        @Schema(allowableValues = {"choice", "short", "step", "essay"})
        String questionType,

        @NotBlank(message = "difficulty는 필수입니다.")
        @Schema(allowableValues = {"low", "mid", "high"})
        String difficulty,

        @NotNull(message = "count는 필수입니다.")
        @Min(value = 0, message = "count는 0 이상이어야 합니다.")
        Integer count
) {
}
