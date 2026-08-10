package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 교사가 학년도 단위 반을 생성하는 요청. */
public record SchoolClassCreateRequest(
        @Min(value = 2000, message = "학년도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "학년도는 2100년 이하여야 합니다.")
        int academicYear,

        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        int grade,

        @NotBlank(message = "반 이름은 필수입니다.")
        @Size(max = 20, message = "반 이름은 20자 이하여야 합니다.")
        String name
) {
}
