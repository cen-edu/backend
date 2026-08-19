package com.cenedu.backend.infra.vector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProblemVectorConfig {
    /** 검색 Provider 호출을 제한하는 두 개의 daemon worker executor를 만든다. */
    @Bean(name = "problemRagSearchExecutor", destroyMethod = "shutdown")
    ExecutorService problemRagSearchExecutor() {
        return Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "problem-rag-search");
            thread.setDaemon(true); return thread;
        });
    }
}
