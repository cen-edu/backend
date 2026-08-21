package com.cenedu.backend.domain.problem.authoring.port;

/** 문제 저작 도메인이 문항 단위 AI 호출 예산을 사용하는 경계다. */
public interface ProblemAiExecutionBudgetPort {
    Scope open(String operationId, String itemId, String sessionId, String operation);
    interface Scope extends AutoCloseable {
        void stage(Stage stage, int candidateAttempt);
        @Override void close();
    }
    enum Stage { ENRICHMENT, GENERATION, VERIFICATION, REPAIR, MODIFICATION }
}
