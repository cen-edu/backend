package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.List;
import java.util.UUID;

/** 검증기가 판정 결과만 반환하는 보고서로, 재시도·저장 정책은 포함하지 않는다. */
public record ProblemVerificationReport(
        UUID requestId,
        VerificationScope scope,
        VerificationOverallStatus overallStatus,
        List<VerificationFinding> findings
) {
}
