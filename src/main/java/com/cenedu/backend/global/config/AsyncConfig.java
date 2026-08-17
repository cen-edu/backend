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
}
