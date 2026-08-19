package com.cenedu.backend.domain.analysis.reissue.row;

import com.cenedu.backend.global.common.enums.EvaluationArea;

/** 한 소단원 × 평가 영역의 문항 단위 채점·오답 분포. */
public record EvaluationAreaEvidenceRow(
        long subUnitId,
        EvaluationArea evaluationArea,
        int gradedItemCount,
        int incorrectItemCount
) {
}
