package com.cenedu.backend.domain.problem.entity.enums;

/** 여러 문항 생성 요청 전체의 진행·부분 실패 상태다. */
public enum GenerationJobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    PARTIALLY_FAILED,
    FAILED
}
