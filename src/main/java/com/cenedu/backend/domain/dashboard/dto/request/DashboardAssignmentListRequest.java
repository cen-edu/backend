package com.cenedu.backend.domain.dashboard.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 반·학기와 페이지로 대시보드 학습지 목록을 조회하는 조건. */
public record DashboardAssignmentListRequest(
        @NotNull(message = "반 ID는 필수입니다.")
        @Positive(message = "반 ID는 양수여야 합니다.")
        Long classId,

        @NotNull(message = "학기는 필수입니다.")
        @Min(value = 1, message = "학기는 1 이상이어야 합니다.")
        @Max(value = 2, message = "학기는 2 이하여야 합니다.")
        Integer semester,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size
) {
    public DashboardAssignmentListRequest {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
    }
}
