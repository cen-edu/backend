package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

import com.cenedu.backend.domain.curriculum.dto.response.CurriculumPathResponse;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;

public record ProblemQuestionDetailResponse(
    Long id,
    CurriculumPathResponse curriculum,
    short difficulty,
    QuestionType questionType,
    QuestionPresentation presentation,
    List<ProblemContentBlockResponse> contentBlocks,
    List<ProblemAssetResponse> assets,
    List<ProblemChoiceResponse> choices,
    List<ProblemStepResponse> steps,
    List<ProblemAnswerUnitResponse> answerUnits,
    String explanation,
    ProblemLearningGuideResponse learningGuide,
    String hintText
) {

    /**
     * 문제 엔티티와 일괄 조회한 하위 데이터를 교사용 상세 응답으로 변환한다.
     */
    public static ProblemQuestionDetailResponse from(
        ProblemQuestion question,
        CurriculumPathResponse curriculum,
        List<ProblemContentBlockResponse> contentBlocks,
        List<ProblemAssetResponse> assets,
        List<ProblemChoiceResponse> choices,
        List<ProblemStepResponse> steps,
        List<ProblemAnswerUnitResponse> answerUnits,
        ProblemLearningGuideResponse learningGuide
    ) {
        return new ProblemQuestionDetailResponse(
            question.getId(),
            curriculum,
            question.getDifficulty(),
            question.getQuestionType(),
            question.getPresentation(),
            List.copyOf(contentBlocks),
            List.copyOf(assets),
            List.copyOf(choices),
            List.copyOf(steps),
            List.copyOf(answerUnits),
            question.getExplanation(),
            learningGuide,
            question.getHintText()
        );
    }
}
