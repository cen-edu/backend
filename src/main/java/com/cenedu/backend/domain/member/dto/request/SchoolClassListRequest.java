package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 반 관리 목록의 학년도, 학년, 반 이름 검색 조건. */
public record SchoolClassListRequest(
        @Min(value = 2000, message = "학년도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "학년도는 2100년 이하여야 합니다.")
        Integer academicYear,

        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        Integer grade,

        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword
) {
}
