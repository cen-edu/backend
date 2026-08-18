package com.cenedu.backend.domain.problem.service;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Problem 생성·자산·검증 수직 호출 전체의 동시 실행 수를 제한한다. */
@Component
public class ProblemAiConcurrencyLimiter {
    private final Semaphore permits;
    private final Duration timeout;

    public ProblemAiConcurrencyLimiter(
            @Value("${app.problem-authoring.ai-max-concurrency:4}") int maxConcurrency,
            @Value("${app.problem-authoring.ai-permit-timeout-seconds:30}") long timeoutSeconds) {
        if (maxConcurrency < 1 || timeoutSeconds < 1) throw new IllegalArgumentException("AI 동시 실행 설정은 양수여야 합니다.");
        this.permits = new Semaphore(maxConcurrency, true);
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /** 제한 시간 안에 permit을 얻고 close 시 반드시 반환한다. */
    public Permit acquire() {
        try {
            if (!permits.tryAcquire(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("문제 AI 동시 실행 permit 대기 시간이 초과됐습니다.");
            }
            return new Permit(permits);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("문제 AI permit 대기가 중단됐습니다.", exception);
        }
    }

    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private boolean released;
        private Permit(Semaphore semaphore) { this.semaphore = semaphore; }
        @Override public void close() { if (!released) { released = true; semaphore.release(); } }
    }
}
