package com.cenedu.backend.domain.grading.dto.response;

import java.math.BigDecimal;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;

import io.swagger.v3.oas.annotations.media.Schema;

/** 점수표의 열 머리(문항). 발문은 담지 않는다 — 점수표는 번호와 배점만 그린다. */
public record GradingQuestionResponse(
        Long worksheetItemId,
        int displayOrder,
        Long questionId,

        @Schema(allowableValues = {"choice", "short", "step", "essay"})
        String format,

        @Schema(allowableValues = {"low", "mid", "high"})
        String difficulty,

        @Schema(description = "일반·맞춤 학습은 null 이다")
        BigDecimal maxScore,

        Long subUnitId
) {

    public static GradingQuestionResponse of(WorksheetItem item, ProblemQuestion question) {
        return new GradingQuestionResponse(
                item.getId(),
                item.getDisplayOrder(),
                question.getId(),
                GradingResponseFormatter.toApiQuestionFormat(question.getQuestionType()),
                GradingResponseFormatter.toApiDifficulty(question.getDifficulty()),
                item.getMaxScore(),
                question.getSubUnitId());
    }
}
