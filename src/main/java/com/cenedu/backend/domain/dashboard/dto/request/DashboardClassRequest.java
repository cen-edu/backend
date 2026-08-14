package com.cenedu.backend.domain.dashboard.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 반과 학기로 대시보드 요약·학생 현황을 조회하는 조건. */
public record DashboardClassRequest(
        @NotNull(message = "반 ID는 필수입니다.")
        @Positive(message = "반 ID는 양수여야 합니다.")
        Long classId,

        @NotNull(message = "학기는 필수입니다.")
        @Min(value = 1, message = "학기는 1 이상이어야 합니다.")
        @Max(value = 2, message = "학기는 2 이하여야 합니다.")
        Integer semester
) {
}
