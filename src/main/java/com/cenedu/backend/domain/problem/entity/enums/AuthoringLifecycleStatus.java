package com.cenedu.backend.domain.problem.entity.enums;

/** 수정 세션이 작업 중인지 최종 문제은행으로 저장됐는지 나타낸다. */
public enum AuthoringLifecycleStatus {
    DRAFT,
    FINALIZED,
    CANCELLED,
    EXPIRED
}
