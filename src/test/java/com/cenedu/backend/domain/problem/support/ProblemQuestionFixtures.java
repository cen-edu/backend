package com.cenedu.backend.domain.problem.support;

import com.cenedu.backend.domain.problem.entity.ProblemQuestion;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.global.common.enums.QuestionType;

public final class ProblemQuestionFixtures {
    private ProblemQuestionFixtures() { }

    public static ProblemQuestion imported() {
        return ProblemQuestion.create(QuestionSourceType.IMPORTED, "dataset:1", "dataset", null,
                1L, "topic", (short) 1, QuestionType.SHORT_INPUT,
                QuestionPresentation.TEXT_ONLY, "{}", "prompt", null, null, null, null);
    }
}
