package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.dto.request.AssessmentGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.AssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssessmentGenerationService {

    private static final Set<QuestionType> ALLOWED_QUESTION_TYPES =
        Set.of(
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.SHORT_INPUT,
            QuestionType.ESSAY
        );

    private final ProblemQuestionSelector problemQuestionSelector;
    private final ProblemQuestionDetailService problemQuestionDetailService;

    /**
     * 종합평가 조건에 맞는 문항을 중복 없이 선택하고 상세 정보를 반환한다.
     */
    public List<ProblemQuestionDetailResponse> generate(
        AssessmentGenerationRequest request
    ) {
        List<ProblemQuestion> allSelectedQuestions =
            new ArrayList<>();

        Set<Long> selectedQuestionIds =
            new HashSet<>();

        for (AssessmentGenerationItemRequest item : request.items()) {
            validateQuestionType(item.questionType());

            List<ProblemQuestion> selectedQuestions =
                problemQuestionSelector.select(
                    item.subUnitId(),
                    item.difficulty(),
                    item.questionType(),
                    item.count(),
                    selectedQuestionIds
                );

            for (ProblemQuestion question : selectedQuestions) {
                selectedQuestionIds.add(question.getId());
            }

            allSelectedQuestions.addAll(selectedQuestions);
        }

        return problemQuestionDetailService.getDetails(
            allSelectedQuestions
        );
    }

    /**
     * 종합평가에서 허용되는 문항 유형인지 확인한다.
     */
    private void validateQuestionType(QuestionType questionType) {
        if (!ALLOWED_QUESTION_TYPES.contains(questionType)) {
            throw new BusinessException(
                ErrorCode.ASSESSMENT_QUESTION_TYPE_NOT_ALLOWED
            );
        }
    }
}
