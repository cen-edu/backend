package com.cenedu.backend.domain.problem.authoring.semantic.model;
import com.cenedu.backend.global.common.enums.*;
public record SemanticProblemIntent(QuestionType questionType,String difficulty,EvaluationArea evaluationArea,String solutionStrategy,String targetKey,int expectedReasoningSteps,boolean visualRequired) {}
