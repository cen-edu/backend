package com.cenedu.backend.domain.problem.entity.enums;

/** 병렬 실행되는 문항 하나의 생성·검증 상태다. */
public enum GenerationItemStatus {
    QUEUED,
    GENERATING,
    VERIFYING,
    SUCCEEDED,
    FAILED
}
