package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 점수표의 셀 하나(학생 × 문항).
 *
 * @param gradingStatus DB 원값 그대로 나간다. 교사는 "아직 안 돌린 것"과 "돌렸는데 실패한 것"을
 *                      구분해야 재실행 여부를 판단한다(명세 2.3)
 */
public record GradingCellResponse(
        Long worksheetItemId,

        @Schema(allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String result,

        BigDecimal score,

        @Schema(allowableValues = {"NOT_GRADED", "GRADED", "FAILED"})
        String gradingStatus,

        @Schema(allowableValues = {"auto", "teacher"})
        String gradedBy
) {
}
