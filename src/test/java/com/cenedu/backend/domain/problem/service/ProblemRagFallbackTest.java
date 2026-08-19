package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;

class ProblemRagFallbackTest {
    @Test
    void disabledRagKeepsCallerReferencesAndNeverResolvesRetrieval() {
        var selector = mock(ProblemQuestionSelector.class);
        var snapshots = mock(ProblemBankSnapshotQueryService.class);
        var retrievalProvider = mock(ObjectProvider.class);
        var traceProvider = mock(ObjectProvider.class);
        var properties = mock(ProblemRagProperties.class);
        when(selector.selectAvailable(30L, (short) 2, QuestionType.SHORT_INPUT, Integer.MAX_VALUE, Set.of()))
                .thenReturn(List.of());
        when(snapshots.getSnapshots(List.of())).thenReturn(List.<BankSnapshotResult>of());
        when(properties.enabled()).thenReturn(false);

        var service = new ProblemGenerationPlanningService(selector, snapshots, retrievalProvider,
                traceProvider, properties);
        var callerReference = mock(GenerationReference.class);
        var requirement = new ProblemGenerationRequirement(30L, (short) 2, QuestionType.SHORT_INPUT, 1,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()), scope(),
                List.of(callerReference), List.of());

        var command = service.plan(UUID.randomUUID(), GenerationJobType.GENERAL_LEARNING, List.of(requirement))
                .slots().getFirst().generationCommand();

        assertThat(command.references()).containsExactly(callerReference);
        assertThat(command.retrievalRequestId()).isNull();
        verifyNoInteractions(retrievalProvider, traceProvider);
    }

    @Test
    void retrievalFailureFallsBackToCallerReferences() {
        var selector = mock(ProblemQuestionSelector.class);
        var snapshots = mock(ProblemBankSnapshotQueryService.class);
        var retrieval = mock(ProblemReferenceRetrievalPort.class);
        var retrievalProvider = mock(ObjectProvider.class);
        var traceProvider = mock(ObjectProvider.class);
        var properties = mock(ProblemRagProperties.class);
        when(selector.selectAvailable(anyLong(), anyShort(), any(), anyInt(), anySet())).thenReturn(List.of());
        when(snapshots.getSnapshots(List.of())).thenReturn(List.of());
        when(properties.enabled()).thenReturn(true);
        when(properties.candidateLimit()).thenReturn(40);
        when(retrievalProvider.getIfAvailable()).thenReturn(retrieval);
        when(retrieval.retrieve(any())).thenThrow(new IllegalStateException("provider down"));
        var service = new ProblemGenerationPlanningService(selector, snapshots, retrievalProvider,
                traceProvider, properties);
        var requirement = new ProblemGenerationRequirement(30L, (short) 2, QuestionType.SHORT_INPUT, 1,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()), scope(),
                List.of(), List.of());

        var command = service.plan(UUID.randomUUID(), GenerationJobType.GENERAL_LEARNING, List.of(requirement))
                .slots().getFirst().generationCommand();

        assertThat(command.references()).isEmpty();
        assertThat(command.retrievalRequestId()).isNotNull();
    }

    private static CurriculumScope scope() {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 30L, "대", "중", "소");
    }
}
