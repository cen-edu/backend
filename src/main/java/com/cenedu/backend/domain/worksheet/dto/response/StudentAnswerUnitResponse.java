package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemAnswerUnit;
import com.cenedu.backend.domain.submission.entity.SubmissionAnswer;
import com.cenedu.backend.global.common.enums.QuestionType;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 채점 칸 하나. 정답 값({@code answerRaw}/{@code answerNormalized})은 절대 담지 않는다.
 *
 * <p>{@code inputMode}는 저장된 답이 없어도 렌더링해야 하므로 {@code submission_answer}가 아니라
 * {@code problem_question.question_type}으로 결정한다(6-3절 저장 규칙과 같은 축).
 */
public record StudentAnswerUnitResponse(
        Long answerUnitId,
        int displayOrder,

        @Schema(description = "입력 방식. DB 원값 그대로(변환 없음)",
                allowableValues = {"CHOICE", "HANDWRITING", "IMAGE"})
        String inputMode,

        Saved saved
) {

    public static StudentAnswerUnitResponse from(
            ProblemAnswerUnit unit, QuestionType questionType, SubmissionAnswer savedAnswer
    ) {
        Saved saved = savedAnswer == null ? Saved.empty() : Saved.from(savedAnswer);
        return new StudentAnswerUnitResponse(
                unit.getId(), unit.getDisplayOrder(), StudentResponseFormatter.toInputMode(questionType), saved);
    }

    /** 이어 풀기 복원 값. {@code selectedChoiceId}는 객관식이 아니면 항상 {@code null}이다. */
    public record Saved(Long selectedChoiceId, String rawLatex, boolean hasHandwriting) {

        static Saved empty() {
            return new Saved(null, null, false);
        }

        static Saved from(SubmissionAnswer answer) {
            return new Saved(
                    answer.getSelectedChoiceId(),
                    answer.getRawLatex(),
                    answer.getAnswerImageRef() != null
            );
        }
    }
}
