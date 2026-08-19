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

        @Schema(description = "내가 고른 보기 ID. 객관식이 아니거나 미제출이면 null")
        Long selectedChoiceId,

        @Schema(description = "정답 보기 ID. 객관식이 아니거나 공개 전이면 null — correctAnswer 와 같은 게이트다")
        Long correctChoiceId,

        @Schema(description = "문항 판정", allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String result,

        BigDecimal score,

        @Schema(description = "필기 이미지가 있는지. S3가 꺼져 있어도 값이 정확하다")
        boolean hasHandwriting,

        @Schema(description = "내가 쓴 필기 이미지의 만료 URL. 필기가 없거나 S3가 꺼져 있으면 null")
        String handwritingUrl
) {
}
