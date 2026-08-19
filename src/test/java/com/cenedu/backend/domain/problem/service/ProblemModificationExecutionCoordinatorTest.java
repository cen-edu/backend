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

    @Test
    void semantic_model이_없는_legacy_fallback도_typed_result로_반환한다() {
        var worker = mock(ProblemModificationWorker.class);
        var versions = mock(ProblemAuthoringVersionRepository.class);
        var version = mock(com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion.class);
        when(version.getId()).thenReturn(20L);
        when(version.getSemanticModel()).thenReturn(null);
        when(versions.findByIdAndSessionId(20L, 31L)).thenReturn(java.util.Optional.of(version));
        when(worker.execute(anyLong(), any())).thenReturn(new com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult(
                21L, 2, java.util.UUID.randomUUID(), null, null, true));
        var coordinator = new ProblemModificationExecutionCoordinator(worker, mock(ProblemAuthoringStateService.class),
                mock(ProblemQuestionSelector.class), mock(ProblemBankSnapshotQueryService.class),
                mock(ProblemAuthoringSessionRepository.class), versions, mock(ProblemAuthoringJsonCodec.class),
                mock(PlatformTransactionManager.class));
        var patch = new ProblemSemanticPatch(1, java.util.UUID.randomUUID(), 20L, SemanticEditMode.PARAMETRIC_PATCH,
                java.util.List.of(), "확인");
        var plan = new ProblemEditExecutionPlan(java.util.UUID.randomUUID(), 31L, 20L, EditAction.MODIFY,
                ReplacementSourcePolicy.NONE, null, java.util.List.of(), patch,
                java.util.List.of(), java.util.List.of(), java.util.List.of(), null);

        var result = (ProblemModificationExecutionResult) coordinator.execute(7L, plan, ProblemSnapshotFixtures.shortInput());

        assertThat(result.previewVersionId()).isEqualTo(21L);
        assertThat(result.legacyFallback()).isTrue();
        assertThat(result.promoted()).isTrue();
    }
}
