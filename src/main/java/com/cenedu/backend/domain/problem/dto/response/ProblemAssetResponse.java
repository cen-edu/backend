package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.entity.ProblemAsset;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.ProblemAssetStorageStatus;

public record ProblemAssetResponse(
    String assetKey,
    AssetRole role,
    short displayOrder,
    String url,
    int widthPx,
    int heightPx,
    String altText,
    ProblemAssetStorageStatus storageStatus
) {

    /**
     * 이미지 엔티티와 접근 URL을 이미지 상세 응답으로 변환한다.
     */
    public static ProblemAssetResponse from(
        ProblemAsset asset,
        String url
    ) {
        return new ProblemAssetResponse(
            asset.getAssetKey(),
            asset.getRole(),
            asset.getDisplayOrder(),
            url,
            asset.getWidthPx(),
            asset.getHeightPx(),
            asset.getAltText(),
            asset.getStorageStatus()
        );
    }
}
