package com.cenedu.backend.domain.problem.authoring.asset;

/** 승인 전 임시 자산의 생성 진행 상태다. */
public enum DraftAssetStatus {
    PLANNED,
    GENERATING,
    READY,
    FAILED
}
