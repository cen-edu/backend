package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * {@code openAiChatOptions} 빈이 {@link OpenAiProperties} 를 그대로 옮기는지 검증한다.
 *
 * <p>이 매핑은 예전엔 호출마다 {@code OpenAiLlmClient} 가 했지만, 이제 {@code OpenAiChatModel} 의
 * 기본 옵션으로 한 번만 구성된다 — 검증 지점도 그에 맞춰 여기로 옮겼다.
 */
class OpenAiClientConfigTest {

    @Test
    @DisplayName("설정한 모델·추론 강도·토큰 한도를 OpenAiChatOptions 에 그대로 싣는다")
    void buildsOpenAiChatOptionsFromProperties() {
        OpenAiProperties properties = new OpenAiProperties(
                "test-key", "gpt-5-mini", "minimal", 3000, Duration.ofSeconds(60), 2, Map.of());

        OpenAiChatOptions options = new OpenAiClientConfig().openAiChatOptions(properties);

        assertThat(options.getModel()).isEqualTo("gpt-5-mini");
        assertThat(options.getReasoningEffort()).isEqualTo("minimal");
        assertThat(options.getMaxCompletionTokens()).isEqualTo(3000);
        // gpt-5-mini 가 1 이외 값을 400 으로 거절한다 — 지정하지 않아야 한다.
        assertThat(options.getTemperature()).isNull();
    }

    @Test
    @DisplayName("gpt-4o-mini에는 지원하지 않는 reasoning_effort를 보내지 않는다")
    void omitsReasoningEffortForGpt4oMini() {
        OpenAiProperties properties = new OpenAiProperties(
                "test-key", "gpt-4o-mini", "minimal", 3000, Duration.ofSeconds(60), 2, Map.of());

        OpenAiChatOptions options = new OpenAiClientConfig().openAiChatOptions(properties);

        assertThat(options.getReasoningEffort()).isNull();
    }

    @Test
    @DisplayName("gpt-5.6-luna에는 설정한 reasoning_effort를 보낸다")
    void includesReasoningEffortForGpt56Luna() {
        OpenAiProperties properties = new OpenAiProperties(
                "test-key", "gpt-5.6-luna", "medium", 3000, Duration.ofSeconds(60), 2, Map.of());

        OpenAiChatOptions options = new OpenAiClientConfig().openAiChatOptions(properties);

        assertThat(options.getReasoningEffort()).isEqualTo("medium");
    }
}
