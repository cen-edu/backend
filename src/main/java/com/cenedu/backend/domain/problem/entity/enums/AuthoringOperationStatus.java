package com.cenedu.backend.domain.problem.entity.enums;

/** Session에서 현재 실행 중인 생성·수정·검증 작업을 나타낸다. */
public enum AuthoringOperationStatus {
    IDLE,
    GENERATING,
    MODIFYING,
    VERIFYING,
    FAILED
}
