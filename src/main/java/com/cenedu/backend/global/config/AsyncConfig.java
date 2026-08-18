package com.cenedu.backend.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 비동기 실행 설정.
 *
 * <p>{@code @EnableAsync}는 어디에 붙이든 효과가 전역이라 도메인 패키지가 아니라 여기에 둔다.
 * 설정의 위치는 소유가 아니라 영향 범위를 따른다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 자동채점 전용 실행기. {@code @Async("gradingTaskExecutor")}로 이름을 지정해 쓴다 —
     * 기본 실행기에 얹으면 다른 도메인이 나중에 {@code @Async}를 붙일 때 같은 풀을 말없이 공유한다.
     *
     * <p>규칙 채점은 DB 왕복이 대부분이라 풀을 작게 잡는다.
     */
    @Bean
    public ThreadPoolTaskExecutor gradingTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("grading-");
        return executor;
    }

    /**
     * AI 분석 문장 생성 전용 실행기.
     * {@code @Qualifier("analysisReportTaskExecutor")} 로 지정해 쓴다.
     *
     * <p>LLM 호출이라 스레드가 대부분 응답을 기다린다. CPU 를 쓰지 않으므로 규칙 채점보다 넉넉히
     * 잡되, 큐는 짧게 둔다. <b>여기서 밀린 시간이 곧 교사 화면의 "생성 중" 시간</b>이라서, 길게
     * 대기시키느니 거절하고 다시 시도하게 하는 편이 낫다.
     *
     * <p>스레드가 영원히 묶이지는 않는다. {@code app.ai.client.timeout} 이 호출을 끊는다.
     */
    @Bean
    public ThreadPoolTaskExecutor analysisReportTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("analysis-report-");
        return executor;
    }
}
