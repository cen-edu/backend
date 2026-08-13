package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessage;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 응답 처리 규칙을 키 없이 검증한다. 실제 호출은 {@code OpenAiLlmClientLiveTest} 가 한다.
 *
 * <p>특히 <b>빈 텍스트를 성공으로 넘기지 않는다</b>는 규칙은 여기서 막지 않으면 회귀해도
 * 아무 테스트도 울리지 않는다. 잘린 응답은 예외가 아니라 정상 응답의 모습으로 온다.
 */
class OpenAiLlmClientTest {

    private static final OpenAiProperties PROPERTIES = new OpenAiProperties(
            "test-key", "gpt-5-mini", "minimal", 3000, Duration.ofSeconds(60), 2);

    private OpenAIClient openAIClient;
    private OpenAiLlmClient llmClient;

    @BeforeEach
    void setUp() {
        openAIClient = mock(OpenAIClient.class, RETURNS_DEEP_STUBS);
        llmClient = new OpenAiLlmClient(openAIClient, PROPERTIES);
    }

    @Test
    @DisplayName("응답 텍스트와 토큰 사용량을 우리 타입으로 돌려준다")
    void returnsTextAndUsage() {
        stubCompletion(completion("stop", "답변", 23, 10, 0));

        LlmResponse response = llmClient.complete("짧게 답한다.", List.of(ChatMessage.user("1 더하기 1은?")));

        assertThat(response.text()).isEqualTo("답변");
        assertThat(response.promptTokens()).isEqualTo(23);
        assertThat(response.completionTokens()).isEqualTo(10);
        assertThat(response.reasoningTokens()).isZero();
    }

    @Test
    @DisplayName("설정한 모델·추론 강도·토큰 한도를 그대로 요청에 싣는다")
    void sendsConfiguredParameters() {
        stubCompletion(completion("stop", "답변", 1, 1, 0));

        llmClient.complete("짧게 답한다.", List.of(ChatMessage.user("1 더하기 1은?")));

        ArgumentCaptor<ChatCompletionCreateParams> captor =
                ArgumentCaptor.forClass(ChatCompletionCreateParams.class);
        verify(openAIClient.chat().completions()).create(captor.capture());

        ChatCompletionCreateParams params = captor.getValue();
        assertThat(params.model().asString()).isEqualTo("gpt-5-mini");
        assertThat(params.reasoningEffort()).hasValueSatisfying(
                effort -> assertThat(effort.asString()).isEqualTo("minimal"));
        assertThat(params.maxCompletionTokens()).hasValue(3000L);
        // 시스템 메시지 1개 + 대화 1턴
        assertThat(params.messages()).hasSize(2);
    }

    @Test
    @DisplayName("텍스트가 비면 예외로 던지고 finishReason 과 토큰을 메시지에 남긴다")
    void throwsWhenContentIsBlank() {
        // 추론 토큰이 한도를 먹어 잘린 모습. content 는 비었는데 HTTP 는 200 이다.
        stubCompletion(completion("length", "", 23, 1200, 1200));

        assertThatThrownBy(() -> llmClient.complete(null, List.of(ChatMessage.user("설명해줘"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_CLIENT_EMPTY_RESPONSE)
                .hasMessageContaining("length")
                .hasMessageContaining("1200");
    }

    @Test
    @DisplayName("SDK 예외를 그대로 흘리지 않고 BusinessException 으로 바꾼다")
    void wrapsSdkException() {
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenThrow(new OpenAIException("429 rate limit"));

        assertThatThrownBy(() -> llmClient.complete(null, List.of(ChatMessage.user("설명해줘"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_CLIENT_CALL_FAILED)
                .hasMessageContaining("429 rate limit");
    }

    private void stubCompletion(ChatCompletion completion) {
        when(openAIClient.chat().completions().create(any(ChatCompletionCreateParams.class)))
                .thenReturn(completion);
    }

    private static ChatCompletion completion(
            String finishReason, String content, long promptTokens, long completionTokens, long reasoningTokens) {
        return ChatCompletion.builder()
                .id("chatcmpl-test")
                .created(0)
                .model("gpt-5-mini-2025-08-07")
                .object_(JsonValue.from("chat.completion"))
                .addChoice(ChatCompletion.Choice.builder()
                        .index(0)
                        .finishReason(ChatCompletion.Choice.FinishReason.of(finishReason))
                        .logprobs(Optional.empty())
                        .message(ChatCompletionMessage.builder()
                                .content(content)
                                .refusal(Optional.empty())
                                .build())
                        .build())
                .usage(CompletionUsage.builder()
                        .promptTokens(promptTokens)
                        .completionTokens(completionTokens)
                        .totalTokens(promptTokens + completionTokens)
                        .completionTokensDetails(CompletionUsage.CompletionTokensDetails.builder()
                                .reasoningTokens(reasoningTokens)
                                .build())
                        .build())
                .build();
    }
}
