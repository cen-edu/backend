package com.cenedu.backend.domain.problem.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
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
     * 재진입 데드락이 난다.
     *
     * <p><b>크기는 RAG 검색 풀(2)을 넘기지 않는다.</b> 실제 검색 동시성은 그 2-스레드 풀이 상한이라
     * fan-out을 더 키워도 추가 병렬 이득은 없고, 오히려 한 계획이 retrieve()를 3개 이상 동시 발사하면
     * 검색 풀에 큐잉되며 그동안 각 retrieve의 2s 타임아웃이 흘러 불필요한 timeout-fallback만 늘어난다.
     * 기본값을 검색 풀과 같은 2로 두어, 대기 태스크는 fan-out 큐에서 머물러 타임아웃 타이머가 아예
     * 시작되지 않도록 한다.
     */
    @Bean(name = "problemPlanningRetrievalExecutor", destroyMethod = "shutdown")
    ExecutorService problemPlanningRetrievalExecutor(
            @Value("${app.problem.rag.planning-fanout:2}") int fanOut) {
        int size = Math.max(1, fanOut);
        return Executors.newFixedThreadPool(size, runnable -> {
            Thread thread = new Thread(runnable, "problem-planning-retrieval");
            thread.setDaemon(true);
            return thread;
        });
    }
}
