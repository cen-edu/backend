package com.cenedu.backend.domain.problem.authoring.verification;

/** 개별 검증 항목의 통과·실패·비대상·처리 오류를 구분한다. */
public enum VerificationFindingStatus {
    PASS,
    FAIL,
    NOT_APPLICABLE,
    ERROR
}
