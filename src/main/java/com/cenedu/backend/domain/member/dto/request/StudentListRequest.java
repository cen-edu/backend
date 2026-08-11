package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 학생 목록의 필터, 정렬, 페이지 요청 조건. */
public record StudentListRequest(
        @Min(value = 2000, message = "등록연도는 2000년 이상이어야 합니다.")
        @Max(value = 2100, message = "등록연도는 2100년 이하여야 합니다.")
        Integer registrationYear,

        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        Integer grade,

        @Positive(message = "반 ID는 양수여야 합니다.")
        Long classId,

        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword,

        StudentSort sort,

        @Min(value = 0, message = "페이지 번호는 0 이상이어야 합니다.")
        Integer page,

        @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
        @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
        Integer size
) {

    /** 정렬값이 없으면 최신 등록순을 반환한다. */
    public StudentSort resolvedSort() {
        return sort == null ? StudentSort.LATEST : sort;
    }

    /** 페이지 번호가 없으면 첫 페이지를 반환한다. */
    public int resolvedPage() {
        return page == null ? 0 : page;
    }

    /** 페이지 크기가 없으면 기본 크기 20을 반환한다. */
    public int resolvedSize() {
        return size == null ? 20 : size;
    }
}
