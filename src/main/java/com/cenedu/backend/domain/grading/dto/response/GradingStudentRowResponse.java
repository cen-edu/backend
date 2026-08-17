package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 점수표의 학생 한 행. <b>제출한 학생만</b> 들어간다(명세 2.5).
 *
 * @param studentNumber 대응 컬럼이 스키마에 없어 현 단계에서는 항상 {@code null}이다
 */
public record GradingStudentRowResponse(
        Long assignmentStudentId,
        Long studentId,

        @Schema(description = "출석번호. 저장하는 컬럼이 없어 현 단계에서는 항상 null 이다")
        Integer studentNumber,

        String name,
        boolean gradingComplete,

        @Schema(description = "종합평가만 값이 있다")
        BigDecimal totalScore,

        List<GradingCellResponse> cells
) {
}
