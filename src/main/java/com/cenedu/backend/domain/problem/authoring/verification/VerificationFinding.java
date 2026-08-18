package com.cenedu.backend.domain.problem.authoring.verification;

/** 검증 항목 하나의 판정·심각도·원인·짧은 근거를 반환한다. */
public record VerificationFinding(
        VerificationCheckType checkType,
        VerificationFindingStatus status,
        VerificationSeverity severity,
        VerificationIssueCode code,
        String message,
        String evidence
) {
}
