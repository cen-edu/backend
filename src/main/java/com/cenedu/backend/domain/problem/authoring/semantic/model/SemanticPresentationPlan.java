package com.cenedu.backend.domain.problem.authoring.semantic.model;

import java.util.List;

public record SemanticPresentationPlan(String questionTemplate, List<SemanticChoiceTemplate> choices,
                                       List<SemanticStepTemplate> steps, String explanationTemplate,
                                       SemanticLearningGuideTemplate learningGuide,
                                       List<SemanticRubricTemplate> rubrics) {
    public SemanticPresentationPlan {
        choices = List.copyOf(choices);
        steps = List.copyOf(steps);
        rubrics = List.copyOf(rubrics);
    }
}
