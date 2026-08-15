package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 칸 하나의 결과. {@code myAnswer}는 {@code raw_latex}를 그대로 내려준다(정규화 값이 아니다) —
 * 학생은 자기가 쓴 것을 봐야 한다(명세 8절).
 */
public record StudentResultAnswerUnitResponse(
        Long answerUnitId,
        int displayOrder,
        String myAnswer,
        String correctAnswer,

        @Schema(description = "문항 판정", allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String result,

        BigDecimal score,
        boolean hasHandwriting
) {
}
