package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.asset.AssetOutputFormat;
import com.cenedu.backend.domain.problem.authoring.asset.AssetProductionMode;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProblemModificationRetryAndVisualRoutingTest {

    @Test
    void 재시도_프롬프트에는_문제_내용이_아닌_실패_코드만_추가한다() {
        var command = new ProblemModificationCommand(UUID.randomUUID(), wholePlan(), snapshot(), null,
                List.of(), List.of(VerificationIssueCode.EDIT_REQUIREMENT_MISSING));

        String prompt = new ModificationPromptStrategy(
                new tools.jackson.databind.ObjectMapper()).create(command);

        assertThat(prompt).contains("retryIssueCodes=[EDIT_REQUIREMENT_MISSING]");
        assertThat(prompt).contains("그래프의 기울기를 양수로 변경");
    }

    @Test
    void 그래프_전체_수정은_이미지_생성_루프로_환류한다() {
        ProblemModificationAdapter adapter = adapter();
        var output = new ProblemGenerationOutput("양의 기울기인 그래프를 고르시오.",
                List.of(), List.of(), List.of(), List.of(), "해설",
                new ProblemGenerationOutput.LearningGuideOutput("그래프", "기울기", List.of()),
                List.of(), List.of(), false, null, null);

        assertThat(adapter.shouldRegenerateCoordinateGraph(
                new ProblemModificationCommand(UUID.randomUUID(), wholePlan(), snapshot()), output))
                .isTrue();
    }

    @Test
    void 그래프와_무관한_해설_수정은_기존_자산을_보존한다() {
        ProblemModificationAdapter adapter = adapter();
        var target = new ProblemEditTargetRef(EditTargetType.EXPLANATION, null);
        var plan = new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L, EditAction.MODIFY,
                ReplacementSourcePolicy.NONE, null,
                List.of(new ProblemEditInstruction(EditTargetType.EXPLANATION, null,
                        EditChangeNature.PRESENTATIONAL, "해설을 쉽게 변경")), null,
                List.of(target), List.of(), List.of(), null);

        assertThat(adapter.shouldRegenerateCoordinateGraph(
                new ProblemModificationCommand(UUID.randomUUID(), plan, snapshot(), null,
                        List.of(new GeneratedAssetPlan("F1", AssetRole.FIGURE,
                                AssetProductionMode.STRUCTURED_RENDER, AssetOutputFormat.SVG,
                                "좌표평면의 직선", null)), List.of()), null))
                .isFalse();
    }

    private ProblemModificationAdapter adapter() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        @SuppressWarnings("unchecked")
        ObjectProvider<ProblemImageGenerationLoop> loopProvider = mock(ObjectProvider.class);
        return new ProblemModificationAdapter(null, mapperProvider, null, null,
                null, null, null, loopProvider);
    }

    private ProblemEditExecutionPlan wholePlan() {
        return new ProblemEditExecutionPlan(UUID.randomUUID(), 1L, 2L, EditAction.REPLACE,
                ReplacementSourcePolicy.GENERATE_ONLY, null,
                List.of(new ProblemEditInstruction(EditTargetType.WHOLE_QUESTION, null,
                        EditChangeNature.STRUCTURAL, "그래프의 기울기를 양수로 변경")), null,
                List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                List.of(), List.of(), null);
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.SHORT_INPUT, QuestionPresentation.WITH_FIGURE,
                        "mid", 10L, null, null, null),
                List.of(
                        new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                                "다음 좌표 그래프에서 기울기를 구하시오.", null, null),
                        new SnapshotContentBlock("CB2", SnapshotBlockKind.FIGURE, 1,
                                null, "F1", null)),
                List.of(new SnapshotAssetReference("F1", "좌표평면의 직선")),
                List.of(), List.of(), List.of(), "기울기를 계산한다.", null, List.of());
    }
}
