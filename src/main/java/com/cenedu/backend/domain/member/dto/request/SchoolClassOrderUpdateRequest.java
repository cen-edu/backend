package com.cenedu.backend.domain.member.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 교사가 소유한 전체 활성 반의 최종 표시 순서. */
public record SchoolClassOrderUpdateRequest(
        @NotEmpty(message = "반 순서는 하나 이상이어야 합니다.")
        List<@NotNull(message = "반 ID는 필수입니다.")
                @Positive(message = "반 ID는 양수여야 합니다.") Long> classIds
) {
}
