package com.cenedu.backend.domain.problem.authoring.generation;

import java.math.BigDecimal;

import com.cenedu.backend.global.common.enums.EvaluationArea;

/** 평가 영역별 채점 표본과 오답 분포다. */
public record GenerationEvaluationAreaEvidence(
        EvaluationArea evaluationArea,
        int gradedItemCount,
        int incorrectItemCount,
        BigDecimal incorrectRate
) {
}
