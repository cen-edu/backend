package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.ai.verification.adapter.VerificationFixtures;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

/** 시각 자산 설명의 불필요·중복·일반 참조 판정을 ASSET_INCONSISTENT로 승격하는지 검증한다. */
class AssetChecksVisualNecessityTest {
    @Test
    void unnecessaryTextDuplicationAndGenericReferenceAreBlocked() {
        for (String issue : new String[]{"UNNECESSARY", "TEXT_DUPLICATION", "GENERIC_REFERENCE"}) {
            var fake = new FakeLlmClient().respondWith("{\"issue\":\"" + issue + "\",\"detail\":\"detail\"}");
            var checks = new AssetChecks(new VerificationLlmClient(fake, new ObjectMapper()));
            var finding = checks.altTextIntegrity(VerificationFixtures.figureSnapshot());

            assertThat(finding.status()).isEqualTo(VerificationFindingStatus.FAIL);
            assertThat(finding.code()).isEqualTo(VerificationIssueCode.ASSET_INCONSISTENT);
        }
    }

    @Test
    void svgContentIsNotSentToAssetJudgementPrompt() {
        var fake = new FakeLlmClient().respondWith(VerificationFixtures.ASSET_OK);
        var checks = new AssetChecks(new VerificationLlmClient(fake, new ObjectMapper()));
        checks.altTextIntegrity(VerificationFixtures.figureSnapshot());

        String prompt = fake.userPrompts.getFirst();
        assertThat(prompt).doesNotContain("<svg", "<path", "base64", "image/svg+xml");
    }
}
