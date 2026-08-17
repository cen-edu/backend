package com.cenedu.backend.domain.problem.authoring.asset;

import java.util.List;

/** Version.asset_manifest JSON의 정본으로 자산 계획과 임시 생성 결과를 함께 담는다. */
public record DraftAssetManifest(
        int schemaVersion,
        List<GeneratedAssetPlan> plans,
        List<DraftAssetArtifact> artifacts
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 후보가 처음 저장될 때 자산 계획만 고정한 manifest를 만든다. */
    public static DraftAssetManifest planned(List<GeneratedAssetPlan> plans) {
        return new DraftAssetManifest(
                CURRENT_SCHEMA_VERSION,
                plans == null ? List.of() : List.copyOf(plans),
                List.of());
    }
}
