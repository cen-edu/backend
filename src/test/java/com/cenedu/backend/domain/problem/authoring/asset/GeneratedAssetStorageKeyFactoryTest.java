package com.cenedu.backend.domain.problem.authoring.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GeneratedAssetStorageKeyFactoryTest {

    @Test
    @DisplayName("생성 자산을 문항 유형·Session·Version 단위의 임시 경로로 분리한다")
    void createsDraftKeyByQuestionTypeAndVersion() {
        AssetProductionContext context = new AssetProductionContext(
                41L, 2, QuestionType.STEP_FILL);

        String key = GeneratedAssetStorageKeyFactory.draftKey(
                context, "F1", AssetOutputFormat.SVG);

        assertThat(key).isEqualTo(
                "questions/generated/step-fill/drafts/41/2/F1.svg");
    }

    @Test
    @DisplayName("최종 자산은 questionId와 checksum을 포함한 S3 key를 사용한다")
    void createsFinalKeyWithQuestionAndChecksum() {
        String key = GeneratedAssetStorageKeyFactory.finalKey(
                901L,
                QuestionType.MULTIPLE_CHOICE,
                "F1",
                "abc123",
                AssetOutputFormat.PNG);

        assertThat(key).isEqualTo(
                "questions/generated/multiple-choice/901/F1-abc123.png");
    }

    @Test
    @DisplayName("경로 탈출을 유발할 수 있는 assetKey를 거부한다")
    void rejectsUnsafeAssetKey() {
        AssetProductionContext context = new AssetProductionContext(
                41L, 2, QuestionType.ESSAY);

        assertThatThrownBy(() -> GeneratedAssetStorageKeyFactory.draftKey(
                context, "../answer", AssetOutputFormat.SVG))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
