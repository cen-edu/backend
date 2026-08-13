package com.cenedu.backend.domain.problem.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record AssessmentGenerationRequest(
//    @Min(value = 1, message = "학기는 1 이상이어야 합니다.")
//    @Max(value = 2, message = "학기는 2 이하여야 합니다.")
//    short semester, // 현재 API 요청 시 사용되지 않아 우선 제외 하였습니다.

    @NotEmpty(message = "출제 조건은 하나 이상 필요합니다.")
    List<
        @NotNull(message = "출제 조건은 null일 수 없습니다.")
        @Valid AssessmentGenerationItemRequest
        > items
) {
}
