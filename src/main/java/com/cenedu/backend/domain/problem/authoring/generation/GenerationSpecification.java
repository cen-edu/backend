package com.cenedu.backend.domain.problem.authoring.generation;

import java.util.List;

import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;

/** 생성할 문제의 유형·난이도·평가영역·진단유형 기대치를 담는다. */
public record GenerationSpecification(
        QuestionType questionType,
        String difficulty,
        EvaluationArea targetEvaluationArea,
        List<DiagnosticType> targetDiagnosticTypes,
        boolean requiresSolutionStructure,
        VisualGenerationRequirement visualRequirement
) {
    public GenerationSpecification {
        visualRequirement = visualRequirement == null ? VisualGenerationRequirement.none() : visualRequirement;
    }

    public GenerationSpecification(QuestionType questionType, String difficulty,
            EvaluationArea targetEvaluationArea, List<DiagnosticType> targetDiagnosticTypes,
            boolean requiresSolutionStructure) {
        this(questionType, difficulty, targetEvaluationArea, targetDiagnosticTypes,
                requiresSolutionStructure, VisualGenerationRequirement.none());
    }

    public GenerationSpecification(QuestionType questionType, String difficulty,
            EvaluationArea targetEvaluationArea, List<DiagnosticType> targetDiagnosticTypes) {
        this(questionType, difficulty, targetEvaluationArea, targetDiagnosticTypes, false,
                VisualGenerationRequirement.none());
    }
}
