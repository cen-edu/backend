package com.cenedu.backend.domain.problem.dto.response;

import java.util.Objects;

import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationStatus;
import com.cenedu.backend.domain.problem.entity.enums.GenerationItemStatus;

/** 생성·검증 내부 상태를 문항 미리보기 화면용 상태로 변환한 값이다. */
public enum AuthoringSlotDisplayStatus {
    QUEUED,
    GENERATING_CONTENT,
    GENERATING_ASSET,
    VALIDATING,
    VERIFYING,
    READY,
    FAILED;

    /** Item·Session·자산 상태를 화면에서 사용할 하나의 상태로 변환한다. */
    public static AuthoringSlotDisplayStatus resolve(
            GenerationItemStatus itemStatus,
            AuthoringOperationStatus operationStatus,
            boolean hasPendingAsset,
            boolean assetReady
    ) {
        Objects.requireNonNull(itemStatus, "itemStatus");
        Objects.requireNonNull(operationStatus, "operationStatus");

        if (itemStatus == GenerationItemStatus.FAILED
                || operationStatus == AuthoringOperationStatus.FAILED) {
            return FAILED;
        }
        if (itemStatus == GenerationItemStatus.QUEUED) {
            return QUEUED;
        }
        if (hasPendingAsset && !assetReady) {
            return GENERATING_ASSET;
        }
        if (operationStatus == AuthoringOperationStatus.VERIFYING
                || itemStatus == GenerationItemStatus.VERIFYING) {
            return VERIFYING;
        }
        if (operationStatus == AuthoringOperationStatus.GENERATING
                || itemStatus == GenerationItemStatus.GENERATING) {
            return GENERATING_CONTENT;
        }
        if (itemStatus == GenerationItemStatus.SUCCEEDED
                && operationStatus == AuthoringOperationStatus.IDLE
                && (!hasPendingAsset || assetReady)) {
            return READY;
        }
        return VALIDATING;
    }
}
