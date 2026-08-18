package com.cenedu.backend.domain.problem.authoring.verification;

/** 하나의 검증 범위에 대한 최종 판정이며 재시도·저장 결정을 포함하지 않는다. */
public enum VerificationOverallStatus {
    PASSED,
    FAILED,
    ERROR
}
