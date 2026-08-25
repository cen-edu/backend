package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.asset.AssetGenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.asset.AssetOutputFormat;
import com.cenedu.backend.domain.problem.authoring.asset.AssetProductionMode;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateGraphDiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramStyle;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramViewport;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotValidationException;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.domain.problem.authoring.visual.ProblemImageRevisionCommand;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;

class ProblemImageGenerationLoopTest {

    @Test
    void 이미지_전략의_첫실패만_교정해_최대두번_시도한다() {
        ProblemCandidateDraft input = mock(ProblemCandidateDraft.class);
        ProblemCandidateDraft output = mock(ProblemCandidateDraft.class);
        VisualSnapshotConsistencyValidator consistency = mock(VisualSnapshotConsistencyValidator.class);
        AtomicInteger calls = new AtomicInteger();
        ProblemImageGenerator generator = new ProblemImageGenerator() {
            @Override
            public VisualReferenceKind kind() {
                return VisualReferenceKind.COORDINATE_GRAPH;
            }

            @Override
            public ProblemCandidateDraft generate(ProblemCandidateDraft candidate, String description,
                    ProblemGenerationCommand command, int attempt, RuntimeException previousFailure) {
                if (calls.getAndIncrement() == 0) throw new IllegalStateException("좌표 범위 오류");
                assertThat(previousFailure).hasMessage("좌표 범위 오류");
                return output;
            }
        };
        var loop = new ProblemImageGenerationLoop(List.of(generator), consistency);

        assertThat(loop.generate(input, visualOutput(true, "COORDINATE_GRAPH", "직선 y=2x"), command()))
                .isSameAs(output);
        assertThat(calls).hasValue(2);
    }

    @Test
    void 이미지가_필요하지_않으면_종류와_설명을_허용하지_않는다() {
        var loop = new ProblemImageGenerationLoop(List.of(),
                mock(VisualSnapshotConsistencyValidator.class));

        assertThatThrownBy(() -> loop.generate(mock(ProblemCandidateDraft.class),
                visualOutput(false, "COORDINATE_GRAPH", "직선"), command()))
                .isInstanceOf(SnapshotValidationException.class)
                .hasMessageContaining("visualRequired=false");
    }

    @Test
    void 자산_검증_환류는_문항_내용을_보존하고_기존_이미지만_제거한다() {
        ProblemCandidateDraft input = imageCandidate();
        VisualSnapshotConsistencyValidator consistency = mock(VisualSnapshotConsistencyValidator.class);
        AtomicInteger calls = new AtomicInteger();
        ProblemImageGenerator generator = new ProblemImageGenerator() {
            @Override
            public VisualReferenceKind kind() {
                return VisualReferenceKind.COORDINATE_GRAPH;
            }

            @Override
            public ProblemCandidateDraft generate(ProblemCandidateDraft base, String description,
                    ProblemGenerationCommand generationCommand, int attempt, RuntimeException previousFailure) {
                calls.incrementAndGet();
                assertThat(base.snapshot().contentBlocks()).singleElement().satisfies(block ->
                        assertThat(block.text()).isEqualTo("다음 그래프를 보고 기울기를 구하시오."));
                assertThat(base.snapshot().assets()).isEmpty();
                assertThat(base.assetPlans()).isEmpty();
                assertThat(base.snapshot().metadata().presentation()).isEqualTo(QuestionPresentation.TEXT_ONLY);
                assertThat(base.snapshot().choices()).isSameAs(input.snapshot().choices());
                assertThat(base.snapshot().answerUnits()).isSameAs(input.snapshot().answerUnits());
                assertThat(description).isEqualTo("원점을 지나는 직선 y=2x");
                assertThat(attempt).isEqualTo(1);
                assertThat(previousFailure).hasMessageContaining("그림 설명이 발문과 어긋납니다");
                return input;
            }
        };
        var loop = new ProblemImageGenerationLoop(List.of(generator), consistency);
        VerificationFinding finding = new VerificationFinding(VerificationCheckType.ASSET_CONSISTENCY,
                VerificationFindingStatus.FAIL, VerificationSeverity.ERROR,
                VerificationIssueCode.ASSET_IMAGE_REGENERATABLE,
                "그림 설명이 발문과 어긋납니다.", "ALTTEXT:MISMATCH");

        ProblemCandidateDraft revised = loop.revise(
                new ProblemImageRevisionCommand(input, command(), 1, List.of(finding)));

        assertThat(calls).hasValue(1);
        assertThat(revised.requestId()).isNotEqualTo(input.requestId());
        assertThat(revised.snapshot()).isSameAs(input.snapshot());
    }

    private ProblemGenerationOutput visualOutput(boolean required, String kind, String description) {
        return new ProblemGenerationOutput("문제", List.of(), List.of(), List.of(), List.of(),
                "해설", new ProblemGenerationOutput.LearningGuideOutput(
                        "개념", "요약", List.of("핵심")),
                List.of(), List.of(), required, kind, description);
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.COMPREHENSIVE_ASSESSMENT_SHORTAGE,
                new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid", null,
                        List.of(), false, VisualGenerationRequirement.none()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1,
                        null, 20L, "변화와 관계", "좌표와 그래프", "좌표평면과 그래프"),
                List.of(), List.of());
    }

    private ProblemCandidateDraft imageCandidate() {
        var diagram = new CoordinateGraphDiagramSpecV1(1, "F1", DiagramKind.COORDINATE_GRAPH,
                new DiagramViewport(640, 480, 24),
                new DiagramStyle("#333", "#fff", "#00f", 2, "sans-serif", 14),
                "X_MIN", "X_MAX", "Y_MIN", "Y_MAX", "X_TICK", "Y_TICK",
                List.of(), List.of(), List.of(), List.of());
        var specification = new AssetGenerationSpecification(1, "원점을 지나는 직선 y=2x",
                List.of("COORDINATE_GRAPH"), List.of(), java.util.Map.of(), java.util.Map.of(), diagram);
        var plan = new GeneratedAssetPlan("F1", AssetRole.FIGURE,
                AssetProductionMode.STRUCTURED_RENDER, AssetOutputFormat.SVG, "좌표평면의 직선",
                specification);
        var snapshot = new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE, QuestionPresentation.WITH_FIGURE,
                        "mid", 20L, null, null, null),
                List.of(
                        new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0,
                                "다음 그래프를 보고 기울기를 구하시오.", null, null),
                        new SnapshotContentBlock("CB2", SnapshotBlockKind.FIGURE, 1,
                                null, "F1", null)),
                List.of(new SnapshotAssetReference("F1", "좌표평면의 직선")),
                List.of(), List.of(), List.of(), "해설", null, List.of());
        return ProblemCandidateDraft.legacy(UUID.randomUUID(), snapshot, List.of(plan),
                new CandidateProvenance(CandidateSourceType.AI_GENERATE, null, List.of()));
    }
}
