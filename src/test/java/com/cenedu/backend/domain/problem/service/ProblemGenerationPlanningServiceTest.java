package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.retrieval.*;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobType;
import com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ProblemGenerationPlanningServiceTest {
    @Test
    void shortageSlotReceivesActualExamplesAndRetrievalId() {
        var selector = mock(ProblemQuestionSelector.class);
        var snapshots = mock(ProblemBankSnapshotQueryService.class);
        var retrieval = mock(ProblemReferenceRetrievalPort.class);
        var retrievalProvider = mock(ObjectProvider.class);
        var traceProvider = mock(ObjectProvider.class);
        var properties = mock(ProblemRagProperties.class);
        when(selector.selectAvailable(30L, (short) 2, QuestionType.SHORT_INPUT, Integer.MAX_VALUE, Set.of())).thenReturn(List.of());
        when(snapshots.getSnapshots(List.of())).thenReturn(List.<BankSnapshotResult>of());
        when(retrievalProvider.getIfAvailable()).thenReturn(retrieval);
        when(properties.enabled()).thenReturn(true);
        when(properties.candidateLimit()).thenReturn(40);
        when(retrieval.retrieve(any())).thenReturn(List.of(
                new RetrievedProblemReference(201L, snapshot(), .9, 1, "hash", "cluster", Set.of())));
        var service = new ProblemGenerationPlanningService(selector, snapshots, retrievalProvider, traceProvider, properties);
        var requirement = new ProblemGenerationRequirement(30L, (short) 2, QuestionType.SHORT_INPUT, 1,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()), scope(), List.of(), List.of());

        var plan = service.plan(UUID.randomUUID(), GenerationJobType.GENERAL_LEARNING, List.of(requirement));

        var command = plan.slots().getFirst().generationCommand();
        assertThat(command.retrievalRequestId()).isNotNull();
        assertThat(command.references()).extracting(GenerationReference::role)
                .containsExactly(GenerationReferenceRole.EXAMPLE);
        assertThat(command.references().getFirst().sourceQuestionId()).isEqualTo(201L);
    }

    private static CurriculumScope scope() { return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 30L, "대", "중", "소"); }
    private static QuestionSnapshotV1 snapshot() { return new QuestionSnapshotV1(1, new com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata(QuestionType.SHORT_INPUT, com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation.TEXT_ONLY, "mid", 30L, null, null, null), List.of(), List.of(), List.of(), List.of(), List.of(), null, null, List.of()); }
}
