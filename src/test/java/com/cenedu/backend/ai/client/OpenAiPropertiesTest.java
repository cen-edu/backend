package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * useCase 별 모델 설정이 기본값을 어떻게 물려받는지 본다.
 *
 * <p>부분만 덮어쓴 설정이 남은 값을 잃지 않는 것이 핵심이다. Spring AI 는 런타임 옵션에
 * 기본 옵션을 물려주지 않으므로, 여기서 빠뜨린 값은 요청에서 조용히 사라진다.
 */
class OpenAiPropertiesTest {

    private static final OpenAiProperties BASE = new OpenAiProperties(
            "test-key", "gpt-5-mini", "minimal", 3000, Duration.ofSeconds(60), 2, Map.of());

    @Test
    @DisplayName("설정이 없는 useCase 는 기본값을 그대로 쓴다")
    void unconfiguredUseCaseFallsBackToDefaults() {
        LlmModelOptions options = BASE.optionsFor(LlmUseCase.VERIFICATION);

        assertThat(options.model()).isEqualTo("gpt-5-mini");
        assertThat(options.reasoningEffort()).isEqualTo("minimal");
        assertThat(options.maxCompletionTokens()).isEqualTo(3000);
    }

    @Test
    @DisplayName("DEFAULT 는 최상위 설정이다")
    void defaultUseCaseUsesTopLevelSettings() {
        LlmModelOptions options = BASE.optionsFor(LlmUseCase.DEFAULT);

        assertThat(options.model()).isEqualTo("gpt-5-mini");
    }

    @Test
    @DisplayName("모델만 덮어써도 추론 강도와 토큰 상한은 기본값으로 채워진다")
    void partialOverrideKeepsRemainingDefaults() {
        OpenAiProperties properties = withVerification(
                new LlmModelOptions("gpt-5", null, null));

        LlmModelOptions options = properties.optionsFor(LlmUseCase.VERIFICATION);

        assertThat(options.model()).isEqualTo("gpt-5");
        assertThat(options.reasoningEffort()).isEqualTo("minimal");
        assertThat(options.maxCompletionTokens()).isEqualTo(3000);
    }

    @Test
    @DisplayName("세 값을 모두 덮어쓸 수 있다 — 검증은 저작과 다른 모델을 써야 한다")
    void fullOverrideApplies() {
        OpenAiProperties properties = withVerification(
                new LlmModelOptions("gpt-5", "medium", 6000L));

        LlmModelOptions options = properties.optionsFor(LlmUseCase.VERIFICATION);

        assertThat(options.model()).isEqualTo("gpt-5");
        assertThat(options.reasoningEffort()).isEqualTo("medium");
        assertThat(options.maxCompletionTokens()).isEqualTo(6000);
        // 저작측이 쓰는 기본 모델과 달라야 이 설정이 의미가 있다.
        assertThat(options.model()).isNotEqualTo(properties.model());
    }

    @Test
    @DisplayName("useCases 가 null 이어도 기동한다 — 설정하지 않은 프로필이 있다")
    void nullUseCasesBecomesEmpty() {
        OpenAiProperties properties = new OpenAiProperties(
                "test-key", "gpt-5-mini", "minimal", 3000, Duration.ofSeconds(60), 2, null);

        assertThat(properties.useCases()).isEmpty();
        assertThat(properties.optionsFor(LlmUseCase.VERIFICATION).model()).isEqualTo("gpt-5-mini");
    }

    @Test
    @DisplayName("useCase 가 null 이면 기본값이다")
    void nullUseCaseFallsBackToDefaults() {
        assertThat(BASE.optionsFor(null).model()).isEqualTo("gpt-5-mini");
    }

    private static OpenAiProperties withVerification(LlmModelOptions options) {
        return new OpenAiProperties(
                BASE.apiKey(), BASE.model(), BASE.reasoningEffort(), BASE.maxCompletionTokens(),
                BASE.timeout(), BASE.maxRetries(), Map.of(LlmUseCase.VERIFICATION, options));
    }
}
