package com.cenedu.backend.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import com.openai.errors.OpenAIException;
import com.openai.models.completions.CompletionUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;

/**
 * 응답 처리 규칙을 키 없이 검증한다. 실제 호출은 {@code OpenAiLlmClientLiveTest} 가 한다.
 *
 * <p>특히 <b>빈 텍스트를 성공으로 넘기지 않는다</b>는 규칙은 여기서 막지 않으면 회귀해도
 * 아무 테스트도 울리지 않는다. 잘린 응답은 예외가 아니라 정상 응답의 모습으로 온다.
 *
 * <p>모델·추론 강도·토큰 한도는 더 이상 호출마다 여기서 싣지 않는다({@link OpenAiChatModel} 이
 * {@link OpenAiClientConfig} 의 {@code OpenAiChatOptions} 빈을 기본값으로 쓴다) —
 * 그 매핑 검증은 {@code OpenAiClientConfigTest} 로 옮겼다.
 */
class OpenAiLlmClientTest {

    private static final OpenAiProperties PROPERTIES = new OpenAiProperties(
            "test-key", "gpt-5-mini", "minimal", 3000, Duration.ofSeconds(60), 2);

    private OpenAiChatModel chatModel;
    private OpenAiLlmClient llmClient;

    @BeforeEach
    void setUp() {
        chatModel = mock(OpenAiChatModel.class);
        llmClient = new OpenAiLlmClient(chatModel, PROPERTIES);
    }

    @Test
    @DisplayName("응답 텍스트와 토큰 사용량을 우리 타입으로 돌려준다")
    void returnsTextAndUsage() {
        stubResponse(chatResponse("stop", "답변", 23, 10, 0));

        LlmResponse response = llmClient.complete("짧게 답한다.", List.of(ChatMessage.user("1 더하기 1은?")));

        assertThat(response.text()).isEqualTo("답변");
        assertThat(response.promptTokens()).isEqualTo(23);
        assertThat(response.completionTokens()).isEqualTo(10);
        assertThat(response.reasoningTokens()).isZero();
    }

    @Test
    @DisplayName("텍스트가 비면 예외로 던지고 finishReason 과 토큰을 메시지에 남긴다")
    void throwsWhenContentIsBlank() {
        // 추론 토큰이 한도를 먹어 잘린 모습. content 는 비었는데 HTTP 는 200 이다.
        stubResponse(chatResponse("length", "", 23, 1200, 1200));

        assertThatThrownBy(() -> llmClient.complete(null, List.of(ChatMessage.user("설명해줘"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_CLIENT_EMPTY_RESPONSE)
                .hasMessageContaining("length")
                .hasMessageContaining("1200");
    }

    @Test
    @DisplayName("SDK 예외를 그대로 흘리지 않고 BusinessException 으로 바꾼다")
    void wrapsSdkException() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new OpenAIException("429 rate limit"));

        assertThatThrownBy(() -> llmClient.complete(null, List.of(ChatMessage.user("설명해줘"))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.AI_CLIENT_CALL_FAILED)
                .hasMessageContaining("429 rate limit");
    }

    private void stubResponse(ChatResponse response) {
        when(chatModel.call(any(Prompt.class))).thenReturn(response);
    }

    private static ChatResponse chatResponse(
            String finishReason, String content, long promptTokens, long completionTokens, long reasoningTokens) {
        CompletionUsage nativeUsage = CompletionUsage.builder()
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .completionTokensDetails(CompletionUsage.CompletionTokensDetails.builder()
                        .reasoningTokens(reasoningTokens)
                        .build())
                .build();

        DefaultUsage usage = new DefaultUsage(
                Math.toIntExact(promptTokens), Math.toIntExact(completionTokens),
                Math.toIntExact(promptTokens + completionTokens), nativeUsage);

        Generation generation = new Generation(
                new AssistantMessage(content),
                ChatGenerationMetadata.builder().finishReason(finishReason).build());

        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .model("gpt-5-mini-2025-08-07")
                .usage(usage)
                .build();

        return ChatResponse.builder()
                .generations(List.of(generation))
                .metadata(metadata)
                .build();
    }
}
