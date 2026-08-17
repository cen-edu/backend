package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** 채점 화면의 문항 하나. 교사가 문항을 하나씩 넘기며 본다(명세 6절). */
public record GradingDetailItemResponse(
        Long worksheetItemId,
        int displayOrder,
        Long questionId,

        @Schema(allowableValues = {"choice", "short", "step", "essay"})
        String format,

        @Schema(allowableValues = {"low", "mid", "high"})
        String difficulty,

        @Schema(description = "일반·맞춤 학습은 null 이다")
        BigDecimal maxScore,

        @Schema(description = "발문. prompt_text 가 아니라 content_blocks 가 정본이다")
        List<GradingContentBlockResponse> contentBlocks,

        String explanation,

        @Schema(description = "학생이 이 문항에 쓴 시간. 기록이 없으면 null")
        Integer timeSpentSeconds,

        @Schema(allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String result,

        List<GradingAnswerUnitResponse> answerUnits
) {
}
