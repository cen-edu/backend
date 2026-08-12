package com.cenedu.backend.domain.member.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 반 상세 모달에서 반 정보와 최종 선택 학생 목록을 저장하는 요청. */
public record SchoolClassUpdateRequest(
        @Min(value = 2000, message = "학년도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "학년도는 2100년 이하여야 합니다.")
        int academicYear,

        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        int grade,

        @NotBlank(message = "반 이름은 필수입니다.")
        @Size(max = 20, message = "반 이름은 20자 이하여야 합니다.")
        String name,

        @NotNull(message = "선택 학생 목록은 필수입니다.")
        List<@NotNull(message = "학생 ID는 null일 수 없습니다.")
                @Positive(message = "학생 ID는 양수여야 합니다.") Long> studentIds
) {
}
