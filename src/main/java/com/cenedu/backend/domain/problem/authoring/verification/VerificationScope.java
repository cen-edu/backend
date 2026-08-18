package com.cenedu.backend.domain.problem.authoring.verification;

/** 내용 검증과 자산 생성·검증을 병렬화하기 위해 검증 범위를 분리한다. */
public enum VerificationScope {
    CONTENT,
    ASSET
}
