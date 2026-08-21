package com.cenedu.backend.ai.client;

import java.util.concurrent.atomic.AtomicInteger;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Component;

/** 문항 하나의 실제 공급자 API 시도 횟수를 원자적으로 제한한다. */
@Component
public class LlmCallBudgetManager {
    private final ThreadLocal<Scope> current = new ThreadLocal<>();

    public Scope open(String operationId, String itemId, String sessionId, String operation, int limit) {
        Scope scope = new Scope(operationId, itemId, sessionId, operation, limit);
        current.set(scope);
        return scope;
    }

    public Scope current() { return current.get(); }

    public void clear(Scope scope) { if (current.get() == scope) current.remove(); }

    public static final class Scope implements AutoCloseable {
        private final String operationId, itemId, sessionId, operation;
        private final int limit;
        private final AtomicInteger used = new AtomicInteger();
        private volatile String stage = "UNKNOWN";
        private volatile int candidateAttempt;

        private Scope(String operationId, String itemId, String sessionId, String operation, int limit) {
            this.operationId = operationId; this.itemId = itemId; this.sessionId = sessionId;
            this.operation = operation; this.limit = limit;
        }
        public void stage(String stage, int candidateAttempt) { this.stage = stage; this.candidateAttempt = candidateAttempt; }
        public int reserve() {
            for (;;) {
                int before = used.get();
                if (before >= limit) throw new BusinessException(ErrorCode.AI_CLIENT_CALL_BUDGET_EXHAUSTED,
                        "AI 호출 예산이 소진되었습니다.");
                if (used.compareAndSet(before, before + 1)) return before + 1;
            }
        }
        public int used() { return used.get(); }
        public int remaining() { return Math.max(0, limit - used()); }
        public String operationId() { return operationId; }
        public String itemId() { return itemId; }
        public String sessionId() { return sessionId; }
        public String operation() { return operation; }
        public String stage() { return stage; }
        public int candidateAttempt() { return candidateAttempt; }
        @Override public void close() { }
    }
}
