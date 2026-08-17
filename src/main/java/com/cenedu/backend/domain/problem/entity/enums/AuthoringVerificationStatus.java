package com.cenedu.backend.domain.problem.entity.enums;

/** 문제은행의 최종 verification_status와 분리된 임시 Version 검증 상태다. */
public enum AuthoringVerificationStatus {
    NOT_STARTED,
    VERIFYING,
    PASSED,
    FAILED,
    ERROR
}
