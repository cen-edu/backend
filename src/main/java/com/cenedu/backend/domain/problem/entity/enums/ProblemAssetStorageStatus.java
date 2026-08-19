package com.cenedu.backend.domain.problem.entity.enums;

/** 최종 S3 객체의 실제 업로드 상태다. */
public enum ProblemAssetStorageStatus {
    PENDING, PROCESSING, RETRY_WAIT, READY, FAILED
}
