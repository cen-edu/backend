package com.cenedu.backend.domain.problem.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
        executor.initialize();
        return executor;
    }
}
