package com.cenedu.backend.domain.problem.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

class ProblemGenerationAsyncConfigTest {

    @Test
    void async_task_inherits_trace_context_and_does_not_leak_it_to_the_next_task() throws Exception {
        ProblemGenerationAsyncConfig config = new ProblemGenerationAsyncConfig();
        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) config.problemGenerationTaskExecutor();
        try {
            MDC.put("traceId", "trace-test");
            String propagated = CompletableFuture.supplyAsync(() -> MDC.get("traceId"), executor)
                    .get();
            assertThat(propagated).isEqualTo("trace-test");

            MDC.clear();
            String leaked = CompletableFuture.supplyAsync(() -> MDC.get("traceId"), executor)
                    .get();
            assertThat(leaked).isNull();
        } finally {
            MDC.clear();
            executor.shutdown();
        }
    }
}
