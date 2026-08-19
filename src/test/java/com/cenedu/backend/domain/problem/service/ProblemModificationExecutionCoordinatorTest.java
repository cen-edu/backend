package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class ProblemModificationExecutionCoordinatorTest {
    @Test
    void restore는_semantic_model이나_worker없이_typed_result를_반환한다() {
        var state = mock(ProblemAuthoringStateService.class);
        var coordinator = new ProblemModificationExecutionCoordinator(mock(ProblemModificationWorker.class), state,
                mock(ProblemQuestionSelector.class), mock(ProblemBankSnapshotQueryService.class),
                mock(ProblemAuthoringSessionRepository.class), mock(ProblemAuthoringVersionRepository.class),
                mock(ProblemAuthoringJsonCodec.class), mock(PlatformTransactionManager.class));
        var plan = new ProblemEditExecutionPlan(java.util.UUID.randomUUID(), 31L, 20L, EditAction.RESTORE,
                ReplacementSourcePolicy.NONE, 19L, java.util.List.of(), java.util.List.of(), java.util.List.of(),
                java.util.List.of(), null);
        var result = (ProblemModificationExecutionResult) coordinator.execute(7L, plan, ProblemSnapshotFixtures.shortInput());
        assertThat(result.mode()).isEqualTo(SemanticEditMode.RESTORE);
        assertThat(result.previewVersionId()).isEqualTo(19L);
        verify(state).restorePassedVersion(7L, 31L, 19L);
    }
}
