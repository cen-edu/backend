package com.cenedu.backend.ai.problem.adapter;

import com.cenedu.backend.ai.client.LlmCallBudgetManager;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAiExecutionBudgetPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 문제 저작 예산 Port를 실제 호출 예산 관리자에 연결한다. */
@Component
public class ProblemAiExecutionBudgetAdapter implements ProblemAiExecutionBudgetPort {
    private final LlmCallBudgetManager manager;
    private final int generationBudget;
    private final int modificationBudget;

    public ProblemAiExecutionBudgetAdapter(LlmCallBudgetManager manager,
            @Value("${app.ai.problem.call-budget.generation:8}") int generationBudget,
            @Value("${app.ai.problem.call-budget.modification:8}") int modificationBudget) {
        this.manager = manager; this.generationBudget = generationBudget; this.modificationBudget = modificationBudget;
    }
    @Override public Scope open(String operationId, String itemId, String sessionId, String operation) {
        int limit = "MODIFICATION".equalsIgnoreCase(operation) ? modificationBudget : generationBudget;
        LlmCallBudgetManager.Scope scope = manager.open(operationId, itemId, sessionId, operation, limit);
        return new Scope() {
            @Override public void stage(Stage stage, int candidateAttempt) { scope.stage(stage.name(), candidateAttempt); }
            @Override public void close() { manager.clear(scope); }
        };
    }
}
