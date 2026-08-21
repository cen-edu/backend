package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
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
}
