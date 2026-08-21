package com.cenedu.backend.domain.problem.config;

import java.util.concurrent.Executor;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.slf4j.MDC;

/** 문제 문항별 병렬 생성에만 사용하는 실행기를 정의한다. */
@Configuration
public class ProblemGenerationAsyncConfig {
    @Bean(name = "problemGenerationTaskExecutor")
    public Executor problemGenerationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("problem-generation-");
        executor.setTaskDecorator(mdcTaskDecorator());
        executor.initialize();
        return executor;
    }

    /** 비동기 Item 실행에서도 요청 추적용 MDC를 작업 스레드로 전달하고 작업 후 복원한다. */
    private TaskDecorator mdcTaskDecorator() {
        return runnable -> {
            Map<String, String> submittingContext = MDC.getCopyOfContextMap();
            return () -> {
                Map<String, String> previousContext = MDC.getCopyOfContextMap();
                try {
                    if (submittingContext == null || submittingContext.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(submittingContext);
                    }
                    runnable.run();
                } finally {
                    if (previousContext == null || previousContext.isEmpty()) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previousContext);
                    }
                }
            };
        };
    }
}
