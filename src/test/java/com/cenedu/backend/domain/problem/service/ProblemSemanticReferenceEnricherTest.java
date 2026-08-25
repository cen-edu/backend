package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.problem.authoring.diagram.CoordinateGraphDiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionResult;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceDescriptor;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProblemSemanticReferenceEnricherTest {

    @Test
    void 추출한_좌표그래프_구조를_원본_시각참조에_보강한다() {
        ProblemSemanticExtractionService extraction = mock(ProblemSemanticExtractionService.class);
        ProblemSemanticModelV1 semantic = mock(ProblemSemanticModelV1.class);
        var diagram = coordinateGraph();
        when(semantic.diagrams()).thenReturn(List.of(diagram));
        when(extraction.ensureQuestionSemantic(10L, scope(), snapshot())).thenReturn(
                new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, semantic, List.of()));

        var result = new ProblemSemanticReferenceEnricher(extraction).enrichWithStatus(command());

        assertThat(result.unsupportedOrigin()).isFalse();
        assertThat(result.command().editInstruction()).isEqualTo("원본의 관계를 바꾸어줘");
        assertThat(result.command().references().getFirst().visualReference())
                .satisfies(visual -> {
                    assertThat(visual.assetKey()).isEqualTo("F1");
                    assertThat(visual.altText()).isEqualTo("정비례 그래프");
                    assertThat(visual.kind()).isEqualTo(VisualReferenceKind.COORDINATE_GRAPH);
                    assertThat(visual.diagramSpec()).isSameAs(diagram);
                });
    }

    @Test
    void 원본_좌표그래프_추출이_안되면_새_그래프_생성으로_전환한다() {
        ProblemSemanticExtractionService extraction = mock(ProblemSemanticExtractionService.class);
        when(extraction.ensureQuestionSemantic(10L, scope(), snapshot())).thenReturn(
                new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null, List.of()));

        var result = new ProblemSemanticReferenceEnricher(extraction).enrichWithStatus(command());

        assertThat(result.unsupportedOrigin()).isFalse();
        assertThat(result.command().specification().visualRequirement().mode())
                .isEqualTo(VisualGenerationMode.REQUIRED);
        assertThat(result.command().specification().visualRequirement().requiredKind())
                .isEqualTo(VisualReferenceKind.COORDINATE_GRAPH);
    }

    private ProblemGenerationCommand command() {
        var visual = new VisualReferenceDescriptor("F1", VisualReferenceKind.COORDINATE_GRAPH,
                AssetRole.FIGURE, "정비례 그래프", null);
        var reference = new GenerationReference(GenerationReferenceRole.ORIGIN, 10L,
                snapshot(), null, visual);
        var specification = new GenerationSpecification(QuestionType.MULTIPLE_CHOICE, "mid",
                null, List.of(), false, new VisualGenerationRequirement(
                        VisualGenerationMode.PRESERVE_ORIGIN,
                        VisualReferenceKind.COORDINATE_GRAPH));
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE, specification, scope(),
                List.of(reference), List.of(), null, "원본의 관계를 바꾸어줘");
    }

    private QuestionSnapshotV1 snapshot() {
        return new QuestionSnapshotV1(1,
                new SnapshotMetadata(QuestionType.MULTIPLE_CHOICE,
                        QuestionPresentation.WITH_FIGURE, "mid", 20L, null, null, null),
                List.of(), List.of(new SnapshotAssetReference("F1", "정비례 그래프")),
                List.of(), List.of(), List.of(), null, null, List.of());
    }

    private CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 20L,
                "변화와 관계", "좌표와 그래프", "좌표평면과 그래프");
    }

    private CoordinateGraphDiagramSpecV1 coordinateGraph() {
        return new CoordinateGraphDiagramSpecV1(1, "F1", DiagramKind.COORDINATE_GRAPH,
                null, null, "xMin", "xMax", "yMin", "yMax", "xTick", "yTick",
                List.of(), List.of(), List.of(), List.of());
    }
}
