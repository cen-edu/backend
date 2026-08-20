package com.cenedu.backend.domain.problem.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 맞춤 문제를 만들 한 소단원의 단계별 수량이다. */
public record CustomProblemGenerationItemRequest(
        @NotNull @Positive Long subUnitId,
        @NotNull @Min(0) @Max(10) Integer reviewCount,
        @NotNull @Min(0) @Max(10) Integer similarCount,
        @NotNull @Min(0) @Max(10) Integer advancedCount
) {

    /** 이 소단원에서 요청한 전체 문항 수를 반환한다. */
    public int totalCount() {
        return reviewCount + similarCount + advancedCount;
    }
}
