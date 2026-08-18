package com.cenedu.backend.ai.client;

import com.openai.client.OpenAIClient;
import com.cenedu.backend.ai.embedding.EmbeddingProperties;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
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
@EnableConfigurationProperties({OpenAiProperties.class, EmbeddingProperties.class})
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

    /**
     * 실제 호출은 이 옵션을 통해 {@link OpenAiChatModel} 이 수행한다. 위 {@link OpenAIClient} 빈과
     * 값을 다시 적는 이유는 {@code OpenAiChatModel.Builder} 가 내부적으로 비동기 클라이언트를 별도로
     * 만들기 때문이다 — 그쪽도 같은 설정을 보게 해 둔다.
     *
     * <p>{@code temperature} 는 지정하지 않는다. gpt-5-mini 는 1 이 아닌 값을 400 으로 거절하는데,
     * 값이 null 이면 Spring AI 가 요청에 아예 싣지 않는다.
     */
    @Bean
    public OpenAiChatOptions openAiChatOptions(OpenAiProperties properties) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(properties.model())
                .maxCompletionTokens(Math.toIntExact(properties.maxCompletionTokens()))
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                .maxRetries(properties.maxRetries());
        if (supportsReasoningEffort(properties.model())) {
            builder.reasoningEffort(properties.reasoningEffort());
        }
        return builder.build();
    }

    /** reasoning_effort를 지원하는 모델에만 해당 요청 필드를 보낸다. */
    static boolean supportsReasoningEffort(String model) {
        if (model == null) return false;
        String normalized = model.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("gpt-5") || normalized.startsWith("o1")
                || normalized.startsWith("o3") || normalized.startsWith("o4");
    }

    /**
     * 위 {@link #openAIClient} 빈을 그대로 감싼다. 스타터({@code spring-ai-starter-model-openai})를
     * 쓰지 않는다 — 자동 설정이 붙으면 API 키 없는 기동 여부가 자동 설정 사정에 좌우된다.
     */
    @Bean
    public OpenAiChatModel openAiChatModel(OpenAIClient openAIClient, OpenAiChatOptions openAiChatOptions) {
        return OpenAiChatModel.builder()
                .openAiClient(openAIClient)
                .options(openAiChatOptions)
                .build();
    }
}
