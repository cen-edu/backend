package com.cenedu.backend.domain.problem.authoring.asset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * 문제은행 조회 교체가 원본 문항의 이미지를 잃지 않는지 고정한다.
 *
 * <p>manifest를 빈 값으로 저장하면 교체된 문항의 미리보기에서 이미지가 사라지고, 이 Version을
 * 기준으로 다시 AI 수정을 걸 때도 자산 정보가 없는 채로 넘어간다.
 */
class DraftAssetManifestBankReuseTest {

    @Test
    void 은행_문항의_저장_키를_준비된_자산으로_옮긴다() {
        var manifest = DraftAssetManifest.forBankReuse(Map.of("A1", "problems/9/A1.svg"));

        assertThat(manifest.schemaVersion()).isEqualTo(DraftAssetManifest.CURRENT_SCHEMA_VERSION);
        assertThat(manifest.plans()).isEmpty();
        assertThat(manifest.artifacts()).singleElement().satisfies(artifact -> {
            assertThat(artifact.assetKey()).isEqualTo("A1");
            assertThat(artifact.draftStorageKey()).isEqualTo("problems/9/A1.svg");
            assertThat(artifact.status()).isEqualTo(DraftAssetStatus.READY);
        });
    }

    @Test
    void 이미지가_없는_문항은_빈_manifest로_남는다() {
        var manifest = DraftAssetManifest.forBankReuse(Map.of());

        assertThat(manifest.plans()).isEmpty();
        assertThat(manifest.artifacts()).isEmpty();
    }
}
