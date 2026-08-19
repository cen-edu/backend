package com.cenedu.backend.domain.analysis.reissue.row;

import com.cenedu.backend.domain.analysis.entity.enums.DiagnosticStage;

/** 한 소단원 × 풀이 단계의 답안 단위 채점·오답 분포. */
public record DiagnosticStageEvidenceRow(
        long subUnitId,
        DiagnosticStage diagnosticType,
        int gradedUnitCount,
        int incorrectUnitCount
) {
}
