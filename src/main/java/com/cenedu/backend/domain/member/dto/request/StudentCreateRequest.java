package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 비밀번호 입력 없이 학생 계정과 프로필을 함께 생성하는 요청. */
public record StudentCreateRequest(
        @NotBlank(message = "이름은 필수입니다.")
        @Size(min = 2, max = 50, message = "이름은 2자 이상 50자 이하여야 합니다.")
        String name,

        @Min(value = 2000, message = "등록연도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "등록연도는 2100년 이하여야 합니다.")
        int registrationYear,

        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        int grade
) {
}
