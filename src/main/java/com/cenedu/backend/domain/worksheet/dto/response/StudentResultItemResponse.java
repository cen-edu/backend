package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.problem.dto.response.ProblemAssetResponse;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.worksheet.entity.WorksheetItem;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 결과 화면의 문항 한 줄. {@code rubric}은 서술형일 때만 값이 있다(배열이거나 null).
 *
 * <p>공개 전이면 {@code explanation}·{@code chatContext}를 서비스가 <b>아예 만들지 않고</b>
 * {@code null}을 넘긴다. 다 만든 뒤 지우는 방식이면 나중에 필드가 늘 때 삭제 목록에서 빠져
 * 그대로 새어 나간다.
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
        StudentResultExplanationResponse explanation,
        StudentResultChatContextResponse chatContext,
        List<StudentResultAnswerUnitResponse> answerUnits,
        List<StudentRubricItemResponse> rubric,

        @Schema(description = "객관식 보기 전체. 객관식이 아니면 null. 정답 표시는 담지 않는다 — "
                + "정답은 answerUnits[].correctAnswer 가 가지고, 공개 전이면 그쪽이 null 이다")
        List<StudentChoiceResponse> choices,

        @Schema(description = "빈칸형 풀이 단계. 빈칸형이 아니면 null. "
                + "segments[].answerUnitId 로 answerUnits 와 이어진다")
        List<StudentStepResponse> steps,

        List<ProblemAssetResponse> assets
) {

    public static StudentResultItemResponse from(
            WorksheetItem item,
            ProblemQuestion question,
            String result,
            BigDecimal score,
            BigDecimal maxScore,
            List<StudentContentBlockResponse> contentBlocks,
            StudentResultExplanationResponse explanation,
            StudentResultChatContextResponse chatContext,
            List<StudentResultAnswerUnitResponse> answerUnits,
            List<StudentRubricItemResponse> rubric,
            List<StudentChoiceResponse> choices,
            List<StudentStepResponse> steps,
            List<ProblemAssetResponse> assets
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
                explanation,
                chatContext,
                List.copyOf(answerUnits),
                rubric,
                choices,
                steps,
                List.copyOf(assets)
        );
    }
}
