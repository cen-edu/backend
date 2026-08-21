package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

class LlmCallBudgetManagerTest {
    @Test
    void 실제_호출_예산을_초과하면_실패한다() {
        LlmCallBudgetManager manager = new LlmCallBudgetManager();
        LlmCallBudgetManager.Scope scope = manager.open("op", "item", "session", "GENERATION", 2);
        scope.reserve(); scope.reserve();
        assertThat(scope.remaining()).isZero();
        assertThatThrownBy(scope::reserve).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.AI_CLIENT_CALL_BUDGET_EXHAUSTED);
    }

    @Test
    void 서로_다른_scope는_카운터를_공유하지_않는다() {
        LlmCallBudgetManager manager = new LlmCallBudgetManager();
        LlmCallBudgetManager.Scope first = manager.open("a", "1", "s", "GENERATION", 1);
        first.reserve();
        LlmCallBudgetManager.Scope second = manager.open("b", "2", "s", "GENERATION", 1);
        second.reserve();
        assertThat(second.used()).isOne();
    }
}
