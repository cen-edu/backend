package com.cenedu.backend.domain.problem.authoring.asset;

/** 생성된 임시 자산의 논리 경로·형식·크기·무결성을 Version manifest에 남긴다. */
public record DraftAssetArtifact(
        String assetKey,
        DraftAssetStatus status,
        String draftStorageKey,
        String contentType,
        Integer widthPx,
        Integer heightPx,
        String checksum,
        int attemptCount,
        String errorCode
) {
}
