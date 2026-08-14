package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;
import com.cenedu.backend.global.common.enums.QuestionType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 풀이 화면의 문항 한 줄. {@code choices}는 객관식일 때만, {@code steps}는 빈칸형일 때만 값이 있다
 * — 둘 다 값을 갖는 형식은 없다.
 */
public record StudentWorksheetItemResponse(
        Long worksheetItemId,
        int displayOrder,
        Long questionId,

        @Schema(description = "문항 형식", allowableValues = {"choice", "short", "step", "essay"})
        String format,

        @Schema(description = "난이도", allowableValues = {"low", "mid", "high"})
        String difficulty,

        Long subUnitId,

        @Schema(description = "지원 방식. 값이 없으면 지원 없음", allowableValues = {"concept", "chat"})
        String supportMode,

        @Schema(description = "맞춤 학습 단계. 맞춤이 아니면 null",
                allowableValues = {"retrace", "basic", "independent"})
        String customStage,

        BigDecimal maxScore,
        List<StudentContentBlockResponse> contentBlocks,
        List<StudentChoiceResponse> choices,
        List<StudentStepResponse> steps,
        List<StudentAnswerUnitResponse> answerUnits
) {

    public static StudentWorksheetItemResponse from(
            WorksheetItem item,
            ProblemQuestion question,
            List<StudentContentBlockResponse> contentBlocks,
            List<StudentChoiceResponse> choices,
            List<StudentStepResponse> steps,
            List<StudentAnswerUnitResponse> answerUnits
    ) {
        QuestionType questionType = question.getQuestionType();
        return new StudentWorksheetItemResponse(
                item.getId(),
                item.getDisplayOrder(),
                question.getId(),
                WorksheetResponseFormatter.toApiQuestionType(questionType),
                WorksheetResponseFormatter.toApiDifficulty(question.getDifficulty()),
                question.getSubUnitId(),
                WorksheetResponseFormatter.toApiSupportMode(item.getSupportMode()),
                WorksheetResponseFormatter.toApiCustomStage(item.getCustomStage()),
                item.getMaxScore(),
                List.copyOf(contentBlocks),
                questionType == QuestionType.MULTIPLE_CHOICE ? List.copyOf(choices) : null,
                questionType == QuestionType.STEP_FILL ? List.copyOf(steps) : null,
                List.copyOf(answerUnits)
        );
    }
}
