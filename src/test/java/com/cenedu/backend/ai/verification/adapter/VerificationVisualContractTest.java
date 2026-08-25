package com.cenedu.backend.ai.verification.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

class VerificationVisualContractTest {

    @Test
    void 콘텐츠_검증은_그래프_altText를_학생용_정보로_함께_받는다() {
        String prompt = VerificationPrompts.contentIntegrityUserPrompt(snapshot(), null, null);

        assertThat(prompt)
                .contains("[그림 설명 — 학생이 그림 대신 확인하는 접근성 정보]")
                .contains("F1: 좌표평면의 점 $(3,8)$과 두 직선 $y=2x+2$, $y=-2x+14$");
    }

    @Test
    void 자산_프롬프트는_표시된_좌표와_함수식을_정답_유출로_판정하지_않는다() {
        assertThat(VerificationPrompts.assetSystemPrompt())
                .contains("정답과 일치하거나 풀이의 결정적 정보여도")
                .contains("오직 MISMATCH만 본다")
                .doesNotContain("- LEAK:");
    }

    @Test
    void 기존_LEAK_응답이_와도_altText를_실패시키지_않는다() {
        VerificationLlmClient llm = mock(VerificationLlmClient.class);
        when(llm.judgeAsset(any())).thenReturn(new VerificationLlmClient.AssetJudgement(
                "LEAK", "F1에 교점 좌표가 있습니다."));

        var finding = new AssetChecks(llm).altTextIntegrity(snapshot());

        assertThat(finding.status()).isEqualTo(VerificationFindingStatus.PASS);
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT,
                        QuestionPresentation.WITH_FIGURE, "mid", 21L,
                        null, null, null),
                List.of(
                        new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                                "다음 그래프에서 두 직선의 교점을 구하시오.", null, null),
                        new SnapshotContentBlock("CB2", SnapshotBlockKind.FIGURE, 1,
                                null, "F1", null)),
                List.of(new SnapshotAssetReference("F1",
                        "좌표평면의 점 $(3,8)$과 두 직선 $y=2x+2$, $y=-2x+14$")),
                List.of(), List.of(),
                List.of(new SnapshotAnswerUnit("MAIN", null, 0, "(3,8)", "(3,8)",
                        CompareMethod.EXACT, null, null)),
                "두 식을 연립하면 교점은 $(3,8)$이다.", null, List.of());
    }
}
