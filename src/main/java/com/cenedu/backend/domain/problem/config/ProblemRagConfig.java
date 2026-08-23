package com.cenedu.backend.domain.problem.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProblemRagProperties.class)
public class ProblemRagConfig {

    /**
     * 계획 단계에서 소단원(요구)별 RAG 검색을 순서를 보존하며 병렬 실행하는 fan-out 풀이다.
     *
     * <p>검색 타임아웃을 강제하는 {@code problemRagSearchExecutor}(2-스레드)와 <b>반드시 분리</b>한다.
     * 그 풀은 {@code retrieve()} 내부에서 다시 submit·block하므로, 같은 풀로 fan-out하면
     * 재진입 데드락이 난다. 실제 임베딩/벡터 동시성은 그 2-스레드 풀이 레이트리밋한다.
     */
    @Bean(name = "problemPlanningRetrievalExecutor", destroyMethod = "shutdown")
    ExecutorService problemPlanningRetrievalExecutor() {
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "problem-planning-retrieval");
            thread.setDaemon(true);
            return thread;
        });
    }
}
