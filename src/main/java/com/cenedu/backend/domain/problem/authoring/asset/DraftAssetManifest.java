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

    /**
     * 이미 문제은행에 적재된 문항의 이미지를 그대로 가리키는 manifest를 만든다.
     *
     * <p>은행 문항은 생성 계획 없이 최종 저장 키만 갖고 있으므로 plans는 비고 artifacts만 채운다.
     * 이 manifest를 만들지 않고 빈 값을 저장하면 미리보기가 문항의 이미지를 찾지 못하고,
     * 이 Version을 기준으로 다시 AI 수정을 걸 때도 자산 정보가 사라진 채로 넘어간다.
     */
    public static DraftAssetManifest forBankReuse(java.util.Map<String, String> assetStorageKeys) {
        if (assetStorageKeys == null || assetStorageKeys.isEmpty()) {
            return new DraftAssetManifest(CURRENT_SCHEMA_VERSION, List.of(), List.of());
        }
        return new DraftAssetManifest(CURRENT_SCHEMA_VERSION, List.of(),
                assetStorageKeys.entrySet().stream()
                        .map(entry -> new DraftAssetArtifact(entry.getKey(), DraftAssetStatus.READY,
                                entry.getValue(), null, null, null, null, 0, null))
                        .toList());
    }
}
