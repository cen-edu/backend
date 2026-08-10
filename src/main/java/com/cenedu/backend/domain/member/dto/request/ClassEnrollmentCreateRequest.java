package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 반에 학생을 배정하는 요청. */
public record ClassEnrollmentCreateRequest(
        @NotNull(message = "학생 ID는 필수입니다.")
        @Positive(message = "학생 ID는 양수여야 합니다.")
        Long studentId
) {
}
