package org.springframework.ai.openai;

import java.lang.reflect.Method;
import java.util.List;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletionCreateParams;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code OpenAiLlmClient} 가 조립한 {@link Prompt} 가 실제로 어떤 요청이 되는지 고정한다.
 *
 * <p><b>이 테스트가 있는 이유.</b> Spring AI 로 갈아탄 뒤, 런타임 옵션을 실은 호출에서
 * {@code maxCompletionTokens} 와 {@code reasoningEffort} 가 요청에서 조용히 빠졌다. 기본 옵션 중
 * {@code model} 만 복사되기 때문이다. 컴파일도 되고 응답도 오므로 <b>어떤 테스트도 이걸 잡지 못했다</b> —
 * 요청 본문을 직접 보기 전까지는. 같은 일이 다시 생기면 여기서 걸린다.
 *
 * <p>네트워크를 타지 않고 전송될 요청 객체까지만 만든다. 실호출 경로 {@code call(Prompt)} 는
 * {@code buildRequestPrompt}(런타임 옵션 + 기본 옵션 병합) → {@code createRequest} 순으로 가므로
 * 그 두 단계를 그대로 밟는다. 앞은 private, 뒤는 package-private 이라 이 패키지에 두고 리플렉션을 쓴다.
 * <b>병합 단계를 건너뛰면 프로덕션 경로가 아니라서 이 결함이 안 보인다.</b>
 */
class SeedPassThroughTest {

    /** {@code ConceptChatEngine.KEYWORD_SEED} 와 같은 값. */
    private static final long KEYWORD_SEED = 7L;

    private static final String MODEL = "gpt-5-mini";
    private static final String REASONING_EFFORT = "minimal";
    private static final int MAX_COMPLETION_TOKENS = 2048;

    /** {@code OpenAiClientConfig.openAiChatModel} 과 같은 모양의 모델 빈. */
    private static OpenAiChatModel model() {
        OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("sk-test-not-used").build();
        return OpenAiChatModel.builder()
                .openAiClient(client)
                // 빌더가 비동기 클라이언트를 따로 만들어서 옵션에도 키가 있어야 build() 가 통과한다.
                .options(OpenAiChatOptions.builder()
                        .model(MODEL)
                        .reasoningEffort(REASONING_EFFORT)
                        .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                        .apiKey("sk-test-not-used")
                        .build())
                .build();
    }

    /** {@code call(Prompt)} 이 실제로 밟는 두 단계를 그대로 재현한다. */
    private static ChatCompletionCreateParams requestFor(Prompt prompt) throws Exception {
        OpenAiChatModel model = model();
        Method buildRequestPrompt = OpenAiChatModel.class
                .getDeclaredMethod("buildRequestPrompt", Prompt.class);
        buildRequestPrompt.setAccessible(true);
        Prompt merged = (Prompt) buildRequestPrompt.invoke(model, prompt);
        return model.createRequest(merged, false);
    }

    /** {@code OpenAiLlmClient.buildPrompt} 의 seed 있는 분기와 같은 모양. */
    private static Prompt promptWithSeed() {
        return new Prompt(
                List.of(new UserMessage("정수가 뭐야")),
                OpenAiChatOptions.builder()
                        .model(MODEL)
                        .reasoningEffort(REASONING_EFFORT)
                        .maxCompletionTokens(MAX_COMPLETION_TOKENS)
                        .seed(Math.toIntExact(KEYWORD_SEED))
                        .build());
    }

    @Test
    @DisplayName("seed 를 주는 호출도 상한과 추론 강도를 그대로 싣는다")
    void seedCallKeepsModelParameters() throws Exception {
        ChatCompletionCreateParams params = requestFor(promptWithSeed());

        assertThat(params.seed()).hasValue(KEYWORD_SEED);
        assertThat(params.model().asString()).isEqualTo(MODEL);
        // 이 둘이 비면 추출 호출만 다른 조건으로 나간다 — task_13 baseline 과 비교가 깨진다.
        assertThat(params.maxCompletionTokens()).hasValue((long) MAX_COMPLETION_TOKENS);
        assertThat(params.reasoningEffort()).isPresent();
        assertThat(params.reasoningEffort().orElseThrow().asString()).isEqualTo(REASONING_EFFORT);
    }

    @Test
    @DisplayName("seed 를 안 주는 호출은 기본 옵션이 그대로 쓰인다")
    void plainCallUsesDefaults() throws Exception {
        ChatCompletionCreateParams params = requestFor(new Prompt(List.of(new UserMessage("정수가 뭐야"))));

        assertThat(params.seed()).isEmpty();
        assertThat(params.model().asString()).isEqualTo(MODEL);
        assertThat(params.maxCompletionTokens()).hasValue((long) MAX_COMPLETION_TOKENS);
        assertThat(params.reasoningEffort().orElseThrow().asString()).isEqualTo(REASONING_EFFORT);
    }

    @Test
    @DisplayName("런타임 옵션에 안 적은 값은 기본 옵션에서 채워지지 않는다 — 이 결함을 고정한다")
    void runtimeOptionsDoNotInheritFromDefaults() throws Exception {
        Prompt seedOnly = new Prompt(
                List.of(new UserMessage("정수가 뭐야")),
                OpenAiChatOptions.builder().seed(Math.toIntExact(KEYWORD_SEED)).build());

        ChatCompletionCreateParams params = requestFor(seedOnly);

        // model 은 복사되지만 나머지는 사라진다. OpenAiLlmClient 가 셋을 다시 적는 근거다.
        assertThat(params.model().asString()).isEqualTo(MODEL);
        assertThat(params.maxCompletionTokens()).isEmpty();
        assertThat(params.reasoningEffort()).isEmpty();
    }
}
