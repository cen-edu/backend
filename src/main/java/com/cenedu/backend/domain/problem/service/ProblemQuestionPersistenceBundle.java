package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.RenderSpecDocument;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;

import com.cenedu.backend.domain.problem.entity.*;

/** 문제 본체와 단방향 하위 Entity를 한 번에 저장하기 위한 영속화 묶음이다. */
public record ProblemQuestionPersistenceBundle(
        ProblemQuestion question,
        List<ProblemChoice> choices,
        List<ProblemStep> steps,
        List<ProblemAnswerUnit> answerUnits,
        List<ProblemRubricItem> rubricItems,
        List<ProblemAsset> assets,
        SemanticModelDocument semanticModel,
        Map<String, RenderSpecDocument> renderSpecs
) {
    public ProblemQuestionPersistenceBundle(ProblemQuestion question,
            List<ProblemChoice> choices, List<ProblemStep> steps,
            List<ProblemAnswerUnit> answerUnits, List<ProblemRubricItem> rubricItems,
            List<ProblemAsset> assets) {
        this(question, choices, steps, answerUnits, rubricItems, assets, null, Map.of());
    }
}
