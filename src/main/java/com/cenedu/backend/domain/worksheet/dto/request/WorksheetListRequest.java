package com.cenedu.backend.domain.worksheet.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 문제 보관함 목록의 탭, 학년, 학기, 검색어 필터. 전부 선택이다. */
public record WorksheetListRequest(
        @Schema(allowableValues = {"all", "practice", "assessment", "custom"})
        String tab,

        @Min(value = 1, message = "grade는 1 이상이어야 합니다.")
        @Max(value = 3, message = "grade는 3 이하여야 합니다.")
        Integer grade,

        @Schema(allowableValues = {"first", "second", "common"})
        String semester,

        @Size(max = 100, message = "q는 100자 이하여야 합니다.")
        String q
) {

    /** tab이 없으면 전체를 반환한다. */
    public String resolvedTab() {
        return (tab == null || tab.isBlank()) ? "all" : tab;
    }
}
