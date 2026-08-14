package com.cenedu.backend.domain.analysis.dto.request;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 취약점 분석 화면의 학습지 선택 목록 조건. */
public record AnalysisAssignmentListRequest(
        @NotNull(message = "반 ID는 필수입니다.")
        @Positive(message = "반 ID는 양수여야 합니다.")
        Long classId,

        @NotNull(message = "학기는 필수입니다.")
        @Min(value = 1, message = "학기는 1 이상이어야 합니다.")
        @Max(value = 2, message = "학기는 2 이하여야 합니다.")
        Integer semester,

        WorksheetType worksheetType
) {
}
