package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 답안 수정 결과(명세 9절).
 *
 * <p>문항 판정과 총점을 함께 돌려준다 — 프론트가 다시 계산하면 서버와 어긋난다.
 */
public record GradingAnswerPatchResponse(
        Long submissionAnswerId,
        BigDecimal finalScore,

        @Schema(allowableValues = {"auto", "teacher"})
        String gradedBy,

        @Schema(description = "이 칸이 속한 문항의 판정",
                allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String itemResult,

        @Schema(description = "이 학생의 현재 점수 합")
        BigDecimal studentTotalScore
) {
}
