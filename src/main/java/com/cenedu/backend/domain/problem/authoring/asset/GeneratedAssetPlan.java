package com.cenedu.backend.domain.problem.authoring.asset;

import com.cenedu.backend.domain.problem.entity.enums.AssetRole;

/** S1 assetKey에 해당하는 임시 자산을 어떤 방식으로 만들지 지시한다. */
public record GeneratedAssetPlan(
        String assetKey,
        AssetRole role,
        AssetProductionMode productionMode,
        AssetOutputFormat outputFormat,
        String altText,
        AssetGenerationSpecification specification
) {
}
