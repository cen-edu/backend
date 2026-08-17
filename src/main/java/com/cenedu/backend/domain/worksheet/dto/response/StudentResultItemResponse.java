package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 결과 화면의 문항 한 줄. {@code rubric}은 서술형일 때만 값이 있다(배열이거나 null).
 *
 * <p>{@code disclose}가 false면 {@code explanation}을 조립 단계에서 <b>아예 채우지 않는다</b>.
 * 다 만든 뒤 지우는 방식이면 나중에 필드가 늘 때 삭제 목록에서 빠져 그대로 새어 나간다.
 */
public record StudentResultItemResponse(
        Long worksheetItemId,
        int displayOrder,
        Long questionId,

        @Schema(description = "문항 형식", allowableValues = {"choice", "short", "step", "essay"})
        String format,

        @Schema(description = "문항 판정", allowableValues = {"correct", "partial", "wrong", "empty", "pending"})
        String result,

        BigDecimal score,
        BigDecimal maxScore,
        Long subUnitId,
        List<StudentContentBlockResponse> contentBlocks,
        String explanation,
        List<StudentResultAnswerUnitResponse> answerUnits,
        List<StudentRubricItemResponse> rubric
) {

    public static StudentResultItemResponse from(
            WorksheetItem item,
            ProblemQuestion question,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            List<StudentContentBlockResponse> contentBlocks,
            List<StudentResultAnswerUnitResponse> answerUnits,
            List<StudentRubricItemResponse> rubric,
            boolean disclose
    ) {
        return new StudentResultItemResponse(
                item.getId(),
                item.getDisplayOrder(),
                question.getId(),
                WorksheetResponseFormatter.toApiQuestionType(question.getQuestionType()),
                result,
                score,
                maxScore,
                question.getSubUnitId(),
                List.copyOf(contentBlocks),
                disclose ? question.getExplanation() : null,
                List.copyOf(answerUnits),
                rubric
        );
    }
}
