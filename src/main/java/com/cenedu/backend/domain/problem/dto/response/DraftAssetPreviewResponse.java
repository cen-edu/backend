package com.cenedu.backend.domain.problem.dto.response;

/** 교사가 확정 전에 확인하는 local draft SVG preview 응답이다. */
public record DraftAssetPreviewResponse(
        long sessionId, long versionId, String assetKey, String contentType,
        int widthPx, int heightPx, String checksum, String dataUrl
) {}
