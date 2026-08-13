package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;

import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 OpenAI 를 한 번 부른다. 모킹으로는 "LLM 이 불리고 텍스트가 돌아온다"를 확인할 수 없다.
 *
 * <p>{@code OPENAI_API_KEY} 가 있을 때만 돈다. CI 에는 키가 없어 통째로 건너뛴다. 키를 가진 사람이
 * 로컬에서 돌려 SDK 계약이 아직 유효한지 확인하는 용도다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다. 이 테스트가 확인할 것은 SDK 호출 경로지 빈 조립이 아닌데,
 * 컨텍스트를 띄우면 DB 와 JWT 시크릿까지 있어야 한다. 대신 {@code application.yaml} 과 같은 값을
 * 여기 적어 둔다. 두 곳이 어긋나면 이 테스트는 통과하고 앱은 다르게 동작한다는 것이 이 선택의 비용이다.
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiLlmClientLiveTest {

    private static OpenAIClient openAIClient;
    private static OpenAiLlmClient llmClient;

    @BeforeAll
    static void setUp() {
        OpenAiProperties properties = new OpenAiProperties(
                System.getenv("OPENAI_API_KEY"),
                "gpt-5-mini",
                "minimal",
                3000,
                Duration.ofSeconds(60),
                2);
        openAIClient = new OpenAiClientConfig().openAIClient(properties);
        llmClient = new OpenAiLlmClient(openAIClient, properties);
    }

    @AfterAll
    static void tearDown() {
        openAIClient.close();
    }

    @Test
    @DisplayName("실제 호출 — 텍스트가 돌아오고 추론 토큰을 쓰지 않는다")
    void callsRealModel() {
        LlmResponse response = llmClient.complete(
                "짧게 답한다.",
                List.of(ChatMessage.user("1 더하기 1은?")));

        System.out.println("[측정] text=" + response.text()
                + ", promptTokens=" + response.promptTokens()
                + ", completionTokens=" + response.completionTokens()
                + ", reasoningTokens=" + response.reasoningTokens());

        assertThat(response.text()).isNotBlank().contains("2");
        // reasoning-effort=minimal 이 실제로 먹는지가 이 단언의 목적이다.
        assertThat(response.reasoningTokens()).isZero();
    }

    @Test
    @DisplayName("한국어와 LaTeX 가 왕복해도 깨지지 않는다")
    void roundTripsKoreanAndLatex() {
        String sent = "$\\angle AOB$";
        LlmResponse response = llmClient.complete(
                "받은 수식을 설명 없이 그대로 다시 출력한다.",
                List.of(ChatMessage.user(sent + " 를 그대로 다시 출력해줘")));

        System.out.println("[측정] sent=" + sent + ", received=" + response.text());

        assertThat(response.text()).contains(sent);
    }
}
