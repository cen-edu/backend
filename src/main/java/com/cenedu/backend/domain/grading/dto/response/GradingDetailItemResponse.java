package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;

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

        List<GradingAnswerUnitResponse> answerUnits,

        @Schema(description = "객관식 보기 전체. 객관식이 아니면 null. 어느 보기가 정답·선택인지는 "
                + "answerUnits[].correctChoiceId·selectedChoiceId 가 가리킨다")
        List<GradingChoiceResponse> choices,

        @Schema(description = "빈칸형 풀이 단계. 빈칸형이 아니면 null. "
                + "segments[].answerUnitId 로 answerUnits 와 이어진다")
        List<GradingStepResponse> steps,

        @Schema(description = "발문 이미지. contentBlocks[].assetRef 와 assets[].assetKey 를 맞춰 렌더한다")
        List<ProblemAssetResponse> assets
) {
}
