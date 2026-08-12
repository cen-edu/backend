package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.dto.request.ProblemGenerationItemRequest;
import com.cenedu.backend.domain.problem.dto.request.ProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionResponse;
import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.global.common.enums.QuestionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProblemGenerationService {

    private final ProblemQuestionSelector problemQuestionSelector;

    /**
     * 학습 문제 생성 조건에 맞는 STEP_FILL 문항을 중복 없이 선택한다.
     */
    public List<ProblemQuestionResponse> generate(
        ProblemGenerationRequest request
    ) {
        List<ProblemQuestionResponse> responses =
            new ArrayList<>();

        Set<Long> selectedQuestionIds =
            new HashSet<>();

        for (ProblemGenerationItemRequest item : request.items()) {
            List<ProblemQuestion> selectedQuestions =
                problemQuestionSelector.select(
                    item.subUnitId(),
                    item.difficulty(),
                    QuestionType.STEP_FILL,
                    item.count(),
                    selectedQuestionIds
                );

            for (ProblemQuestion question : selectedQuestions) {
                selectedQuestionIds.add(question.getId());
                responses.add(
                    ProblemQuestionResponse.from(question)
                );
            }
        }

        return responses;
    }
}
