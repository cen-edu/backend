package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 학생 한 명의 채점 화면(명세 6절).
 *
 * @param studentNumber 대응 컬럼이 스키마에 없어 현 단계에서는 항상 {@code null}이다
 */
public record GradingStudentDetailResponse(
        Long assignmentStudentId,
        String studentName,

        @Schema(description = "출석번호. 저장하는 컬럼이 없어 현 단계에서는 항상 null 이다")
        Integer studentNumber,

        OffsetDateTime submittedAt,

        @Schema(description = "종합평가만 값이 있다")
        BigDecimal totalScore,

        List<GradingDetailItemResponse> items
) {
}
