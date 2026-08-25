package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.ai.client.LlmCallBudgetManager;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProblemAiExecutionBudgetAdapterTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LlmCallBudgetManager.class)
            .withBean(ProblemAiExecutionBudgetAdapter.class);

    @Test
    void 생성_재시도_정상_경로의_12회_호출을_허용한다() {
        contextRunner.run(context -> {
            LlmCallBudgetManager manager = context.getBean(LlmCallBudgetManager.class);
            ProblemAiExecutionBudgetAdapter adapter =
                    context.getBean(ProblemAiExecutionBudgetAdapter.class);

            try (var ignored = adapter.open("operation", "item", "session", "GENERATION")) {
                LlmCallBudgetManager.Scope budget = manager.current();
                assertThat(budget).isNotNull();
                for (int expected = 1; expected <= 12; expected++) {
                    assertThat(budget.reserve()).isEqualTo(expected);
                }
                assertThatThrownBy(budget::reserve)
                        .isInstanceOf(BusinessException.class)
                        .extracting(exception -> ((BusinessException) exception).getErrorCode())
                        .isEqualTo(ErrorCode.AI_CLIENT_CALL_BUDGET_EXHAUSTED);
            }
            assertThat(manager.current()).isNull();
        });
    }
}
