package com.cenedu.backend.domain.grading.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 평가 결과 목록의 필터. 전부 선택이며 {@code all}은 파라미터 생략으로 표현한다(명세 4절).
 *
 * @param status 서버가 파생하는 값이라(명세 2.4) DB 조회로 거르지 못하고 계산 후에 거른다
 */
public record GradingListRequest(

        @Schema(description = "학년", example = "1")
        @Min(1) @Max(3)
        Integer grade,

        @Schema(description = "반 ID")
        Long classId,

        @Schema(description = "학기. DB 원값 그대로 받는다", allowableValues = {"1", "2", "COMMON"})
        @Pattern(regexp = "1|2|COMMON")
        String semester,

        @Schema(description = "채점 상태", allowableValues = {"grading", "graded", "confirmed"})
        @Pattern(regexp = "grading|graded|confirmed")
        String status
) {

    /** {@code worksheet.grade}가 {@code smallint}라 조회 파라미터 타입을 맞춘다. */
    public Short gradeAsShort() {
        return grade == null ? null : grade.shortValue();
    }
}
