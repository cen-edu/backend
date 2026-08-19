package com.cenedu.backend.domain.worksheet.dto.request;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 문제 생성 결과를 문제 보관함에 확정 저장하는 요청. */
public record WorksheetCreateRequest(
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 100, message = "title은 100자 이하여야 합니다.")
        String title,

        @NotBlank(message = "type은 필수입니다.")
        @Schema(allowableValues = {"practice", "assessment"})
        String type,

        @NotBlank(message = "origin은 필수입니다.")
        @Schema(allowableValues = {"manual", "custom"})
        String origin,

        @NotNull(message = "grade는 필수입니다.")
        @Min(value = 1, message = "grade는 1 이상이어야 합니다.")
        @Max(value = 3, message = "grade는 3 이하여야 합니다.")
        Integer grade,

        @NotBlank(message = "semester는 필수입니다.")
        @Schema(allowableValues = {"first", "second", "common"})
        String semester,

        Long sourceAssignmentId,

        @Schema(description = "직전 차수 학습지 ID. origin=custom 이면 필수이고 아니면 금지한다. "
                + "1차 맞춤은 원본 학습지를, 2차는 1차 맞춤을 가리킨다. 차수는 이 체인의 깊이로 파생한다",
                nullable = true)
        Long parentWorksheetId,

        @NotNull(message = "genSpec은 필수입니다.")
        @Valid
        List<WorksheetGenSpecRequest> genSpec,

        @NotEmpty(message = "items는 1개 이상이어야 합니다.")
        @Valid
        List<WorksheetItemRequest> items
) {
}
