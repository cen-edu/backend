package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumContext;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

/** 생성·수정 후보가 만족해야 할 유형·난이도·교육과정·자산 기대치다. */
public record VerificationExpectation(
        QuestionType expectedQuestionType,
        String expectedDifficulty,
        CurriculumContext expectedCurriculum,
        EvaluationArea targetEvaluationArea,
        List<DiagnosticType> targetDiagnosticTypes,
        List<String> requiredAssetKeys
) {
}
