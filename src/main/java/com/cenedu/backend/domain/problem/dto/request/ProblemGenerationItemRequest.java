package com.cenedu.backend.domain.problem.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ProblemGenerationItemRequest(
    @NotNull(message = "소단원 ID는 필수입니다.")
    Long subUnitId,

    @NotNull(message = "난이도는 필수입니다.")
    @Min(value = 1, message = "난이도는 1 이상이어야 합니다.")
    @Max(value = 3, message = "난이도는 3 이하여야 합니다.")
    Short difficulty,

    @NotNull(message = "문항 수는 필수입니다.")
    @Min(value = 1, message = "문항 수는 1 이상이어야 합니다.")
    @Max(value = 30, message = "문항 수는 30 이하여야 합니다.")
    Integer count
) {
}
