package com.cenedu.backend.ai.client;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link OpenAIClient} 빈을 직접 만든다.
 *
 * <p>{@code openai-java-spring-boot-starter} 를 쓰지 않는 이유는 {@code build.gradle} 주석에 있다 —
 * 그 스타터는 Spring Boot 2 기준이라 이 프로젝트(Boot 4.1.0)와 맞지 않는다. 자동 설정이 없으니
 * 타임아웃과 재시도 횟수를 여기서 명시한다. 기본값에 맡기면 한 번의 호출이 얼마나 오래 매달릴지,
 * 몇 번 재시도할지가 라이브러리 버전에 따라 조용히 달라진다.
 *
 * <p>{@code @EnableConfigurationProperties} 를 여기 붙였다. {@code BackendApplication} 은
 * {@code ai/client} 밖의 파일이라 고치지 않는다.
 */
@Configuration
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiClientConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiClientConfig.class);

    /**
     * {@code destroyMethod} 를 지정해 컨텍스트 종료 시 커넥션 풀과 스레드를 반납한다.
     *
     * <p>키가 비어 있어도 빈은 만든다. 여기서 기동을 막으면 챗봇을 쓰지 않는 팀원까지
     * 키를 받아야 앱을 띄울 수 있다. 대신 호출 시점에 실패한다.
     */
    @Bean(destroyMethod = "close")
    public OpenAIClient openAIClient(OpenAiProperties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("OPENAI_API_KEY 가 비어 있다. 앱은 기동하지만 LLM 호출은 실패한다.");
        }

        return OpenAIOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries())
                .build();
    }
}
