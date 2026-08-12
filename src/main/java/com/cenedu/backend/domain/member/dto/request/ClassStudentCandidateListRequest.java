package com.cenedu.backend.domain.member.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 반 만들기에서 선택할 학생의 학년과 이름 검색 조건. */
public record ClassStudentCandidateListRequest(
        @NotNull(message = "학년은 필수입니다.")
        @Min(value = 1, message = "학년은 1 이상이어야 합니다.")
        @Max(value = 3, message = "학년은 3 이하여야 합니다.")
        Integer grade,

        @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
        String keyword
) {
}
