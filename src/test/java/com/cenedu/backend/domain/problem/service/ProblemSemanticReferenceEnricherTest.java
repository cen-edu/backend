package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.ai.problem.adapter.semantic.SemanticAuthoringProperties;

class ProblemSemanticReferenceEnricherTest {
    @Test
    void origin만_추출하고_example은_snapshot을_유지한다() {
        var service = mock(ProblemSemanticExtractionService.class);
        var model = mock(ProblemSemanticModelV1.class);
        var origin = new GenerationReference(GenerationReferenceRole.ORIGIN, 41L,
                ProblemSnapshotFixtures.shortInput());
        var example = new GenerationReference(GenerationReferenceRole.EXAMPLE, 42L,
                ProblemSnapshotFixtures.shortInput());
        when(service.ensureQuestionSemantic(eq(41L), any(), any())).thenReturn(
                new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, model, List.of()));
        var command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                        "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"), List.of(origin, example), List.of());

        var enriched = new ProblemSemanticReferenceEnricher(service).enrich(command);

        assertThat(enriched.references().get(0).semanticModel()).isSameAs(model);
        assertThat(enriched.references().get(1).semanticModel()).isNull();
        verify(service).ensureQuestionSemantic(eq(41L), any(), any());
        verifyNoMoreInteractions(service);
    }

    @Test
    void personalized_application은_example을_최대_두개만_추출한다() {
        var service = mock(ProblemSemanticExtractionService.class);
        var model = mock(ProblemSemanticModelV1.class);
        var refs = java.util.stream.IntStream.rangeClosed(1, 3)
                .mapToObj(i -> new GenerationReference(GenerationReferenceRole.EXAMPLE, (long) i,
                        ProblemSnapshotFixtures.shortInput())).toList();
        when(service.ensureQuestionSemantic(anyLong(), any(), any())).thenReturn(
                new SemanticExtractionResult(SemanticExtractionStatus.EXTRACTED, model, List.of()));
        var command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.PERSONALIZED_APPLICATION,
                new GenerationSpecification(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                        "mid", null, List.of(), true),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"), refs, List.of());

        var enriched = new ProblemSemanticReferenceEnricher(service).enrich(command);

        assertThat(enriched.references().get(0).semanticModel()).isSameAs(model);
        assertThat(enriched.references().get(1).semanticModel()).isSameAs(model);
        assertThat(enriched.references().get(2).semanticModel()).isNull();
        verify(service, times(2)).ensureQuestionSemantic(anyLong(), any(), any());
    }

    @Test
    void origin_실패는_unsupported_상태로_노출한다() {
        var service = mock(ProblemSemanticExtractionService.class);
        var origin = new GenerationReference(GenerationReferenceRole.ORIGIN, 41L,
                ProblemSnapshotFixtures.shortInput());
        when(service.ensureQuestionSemantic(anyLong(), any(), any())).thenReturn(
                new SemanticExtractionResult(SemanticExtractionStatus.UNSUPPORTED, null, List.of("unsupported")));
        var command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                        "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"), List.of(origin), List.of());

        var result = new ProblemSemanticReferenceEnricher(service).enrichWithStatus(command);

        assertThat(result.unsupportedOrigin()).isTrue();
        assertThat(result.command().references().getFirst().semanticModel()).isNull();
    }

    @Test
    void semantic_비활성이면_참고문제를_그대로_반환하고_extraction을_호출하지_않는다() {
        var service = mock(ProblemSemanticExtractionService.class);
        var origin = new GenerationReference(GenerationReferenceRole.ORIGIN, 41L,
                ProblemSnapshotFixtures.shortInput());
        var command = new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                        "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"), List.of(origin), List.of());

        var result = new ProblemSemanticReferenceEnricher(service,
                new SemanticAuthoringProperties(false)).enrichWithStatus(command);

        assertThat(result.command()).isSameAs(command);
        assertThat(result.unsupportedOrigin()).isFalse();
        verifyNoInteractions(service);
    }
}
