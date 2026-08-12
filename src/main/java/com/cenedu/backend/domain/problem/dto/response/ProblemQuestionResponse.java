package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.global.common.enums.QuestionType;

public record ProblemQuestionResponse(
    Long id,
    Long subUnitId,
    short difficulty,
    QuestionType questionType,
    String contentBlocks,
    String explanation,
    String learningGuide,
    String hintText
) {

    /**
     * 문제 엔티티를 문제 생성 결과 응답으로 변환한다.
     */
    public static ProblemQuestionResponse from(ProblemQuestion question) {
        return new ProblemQuestionResponse(
            question.getId(),
            question.getSubUnitId(),
            question.getDifficulty(),
            question.getQuestionType(),
            question.getContentBlocks(),
            question.getExplanation(),
            question.getLearningGuide(),
            question.getHintText()
        );
    }
}
