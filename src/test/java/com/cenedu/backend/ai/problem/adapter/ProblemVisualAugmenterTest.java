package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;

class ProblemVisualAugmenterTest {

    private static final String GRAPH = """
            {"xMin":-5,"xMax":5,"yMin":-5,"yMax":5,"xTick":1,"yTick":1,
             "points":[{"x":2,"y":3,"label":"A","marker":"CLOSED_CIRCLE"}],
             "segments":[],"lines":[],
             "functions":[{"kind":"DIRECT_PROPORTION","coefficient":2,"label":"y=2x"}]}
            """;

    @Test
    void literalGraphIsConvertedRenderedAndAttachedAsFigure() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse(GRAPH, 1, 1, 0));

        var result = augmenter(client).augment(baseCandidate(), "좌표평면 위 점 A와 직선 y=2x",
                mock(ProblemGenerationCommand.class));

        assertThat(result.snapshot().metadata().presentation()).isEqualTo(QuestionPresentation.WITH_FIGURE);
        assertThat(result.snapshot().assets()).extracting(a -> a.assetKey()).containsExactly("F1");
        assertThat(result.snapshot().contentBlocks())
                .anySatisfy(b -> {
                    assertThat(b.blockKind()).isEqualTo(SnapshotBlockKind.FIGURE);
                    assertThat(b.assetRef()).isEqualTo("F1");
                });
        assertThat(result.assetPlans()).extracting(p -> p.assetKey()).containsExactly("F1");
    }

    @Test
    void invalidMarkerFailsSoBrokenVisualIsNotAttached() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any()))
                .thenReturn(new LlmResponse(GRAPH.replace("CLOSED_CIRCLE", "SQUARE"), 1, 1, 0));

        assertThatThrownBy(() -> augmenter(client).augment(baseCandidate(), "설명",
                mock(ProblemGenerationCommand.class)))
                .isInstanceOf(RuntimeException.class);
    }

    @SuppressWarnings("unchecked")
    private ProblemVisualAugmenter augmenter(LlmClient client) {
        ObjectProvider<ObjectMapper> mapper = mock(ObjectProvider.class);
        when(mapper.getIfAvailable(any())).thenReturn(new ObjectMapper());
        return new ProblemVisualAugmenter(client, mapper, new SnapshotStructuralValidator());
    }

    private ProblemCandidateDraft baseCandidate() {
        return ProblemCandidateDraft.legacy(UUID.randomUUID(), ProblemSnapshotFixtures.shortInput(),
                List.of(), new CandidateProvenance(CandidateSourceType.AI_GENERATE, null, List.of()));
    }
}
