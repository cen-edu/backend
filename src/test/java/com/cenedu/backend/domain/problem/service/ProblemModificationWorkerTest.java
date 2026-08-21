package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import org.springframework.beans.factory.ObjectProvider;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAiExecutionBudgetPort;
import org.junit.jupiter.api.Test;

class ProblemModificationWorkerTest {
    @Test
    void 예산_scope는_단계와_후보시도를_전달한다() {
        ProblemAiExecutionBudgetPort.Scope scope = mock(ProblemAiExecutionBudgetPort.Scope.class);
        scope.stage(ProblemAiExecutionBudgetPort.Stage.MODIFICATION, 1);
        verify(scope).stage(ProblemAiExecutionBudgetPort.Stage.MODIFICATION, 1);
        assertThat(scope).isNotNull();
    }

    @Test
    void 공급자_오류가_반복되어도_수정은_최대_두번만_호출한다() {
        ObjectProvider<ProblemModificationPort> provider = mock(ObjectProvider.class);
        ProblemModificationPort port = mock(ProblemModificationPort.class);
        when(provider.getIfAvailable()).thenReturn(port);
        when(port.modify(any())).thenThrow(new IllegalStateException("provider"));
        ProblemCandidateProcessingService processing = mock(ProblemCandidateProcessingService.class);
        ProblemAuthoringSessionRepository sessions = mock(ProblemAuthoringSessionRepository.class);
        ProblemAuthoringStateService state = mock(ProblemAuthoringStateService.class);
        ProblemModificationCommand command = mock(ProblemModificationCommand.class);
        ProblemEditExecutionPlan plan = mock(ProblemEditExecutionPlan.class);
        when(command.requestId()).thenReturn(java.util.UUID.randomUUID());
        when(command.plan()).thenReturn(plan);
        when(plan.sessionId()).thenReturn(10L);
        when(plan.baseVersionId()).thenReturn(20L);

        ProblemModificationWorker worker = new ProblemModificationWorker(provider, processing, sessions, state);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> worker.execute(7L, command))
                .isInstanceOf(IllegalStateException.class);
        verify(port, times(2)).modify(any());
        verify(state).failOperation(7L, 10L, "MODIFICATION_FAILED");
        verifyNoInteractions(processing);
    }
}
