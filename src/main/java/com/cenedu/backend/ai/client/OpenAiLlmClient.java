package com.cenedu.backend.ai.client;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import com.openai.errors.OpenAIException;
import com.openai.models.completions.CompletionUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;

/**
 * Spring AI({@link OpenAiChatModel})로 {@link LlmClient} 를 구현한다.
 *
 * <p>SDK 예외를 위로 흘리지 않고 {@link BusinessException} 으로 바꾼다. 도메인 코드가
 * {@code com.openai.errors} 를 import 하기 시작하면 SDK 를 갈아끼울 때 호출부까지 전부 열어야 한다.
 * {@code OpenAiChatModel} 은 이 예외를 감싸지 않고 그대로 던지므로 처리 방식은 이전과 같다.
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final OpenAiChatModel chatModel;
    private final OpenAiProperties properties;

    public OpenAiLlmClient(OpenAiChatModel chatModel, OpenAiProperties properties) {
        this.chatModel = chatModel;
        this.properties = properties;
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<ChatMessage> messages) {
        Prompt prompt = buildPrompt(systemPrompt, messages);

        long startedAt = System.nanoTime();
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (OpenAIException e) {
            // 재시도는 SDK 가 max-retries 만큼 이미 끝낸 뒤다. 여기 오면 최종 실패다.
            log.warn("LLM 호출 실패 — model={}, elapsedMs={}", properties.model(), elapsedMs(startedAt), e);
            throw new BusinessException(
                    ErrorCode.AI_CLIENT_CALL_FAILED,
                    "LLM 호출에 실패했습니다: " + e.getMessage());
        }
        long elapsedMs = elapsedMs(startedAt);

        if (response.getResults().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CLIENT_EMPTY_RESPONSE, "LLM 응답에 선택지가 없습니다.");
        }
        Generation generation = response.getResult();
        String finishReason = generation.getMetadata().getFinishReason();

        Usage usage = response.getMetadata().getUsage();
        long promptTokens = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0L;
        long completionTokens = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0L;
        // Usage#getNativeUsage() 는 OpenAiChatModel 내부에서 openai-java 의 CompletionUsage 를
        // 그대로 감싼 값이다. 추론 토큰은 Spring AI 의 Usage 인터페이스가 노출하지 않아 여기서 꺼낸다.
        long reasoningTokens = 0L;
        if (usage.getNativeUsage() instanceof CompletionUsage nativeUsage) {
            reasoningTokens = nativeUsage.completionTokensDetails()
                    .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                    .orElse(0L);
        }

        String text = generation.getOutput().getText();

        // 프롬프트 본문과 응답 본문은 남기지 않는다. 학생 입력과 시험 문항이 로그로 나가면
        // 정답 유출 정책이 무너진다. 길이만으로도 대부분의 추적은 된다.
        log.info("LLM 호출 — model={}, elapsedMs={}, promptTokens={}, completionTokens={},"
                        + " reasoningTokens={}, finishReason={}, responseLength={}",
                response.getMetadata().getModel(), elapsedMs, promptTokens, completionTokens, reasoningTokens,
                finishReason, text != null ? text.length() : 0);

        if (text == null || text.isBlank()) {
            // 빈 문자열을 정상 응답으로 흘리면 챗봇이 빈 말풍선을 띄우고 원인을 못 찾는다.
            // 추론 토큰이 한도를 먹어 잘린 경우가 대표적이라 finishReason 과 토큰을 같이 남긴다.
            throw new BusinessException(ErrorCode.AI_CLIENT_EMPTY_RESPONSE,
                    "LLM 응답 텍스트가 비어 있습니다 — finishReason=%s, completionTokens=%d, reasoningTokens=%d, maxCompletionTokens=%d"
                            .formatted(finishReason, completionTokens, reasoningTokens,
                                    properties.maxCompletionTokens()));
        }

        return new LlmResponse(text, promptTokens, completionTokens, reasoningTokens);
    }

    private Prompt buildPrompt(String systemPrompt, List<ChatMessage> messages) {
        List<Message> springMessages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            springMessages.add(new SystemMessage(systemPrompt));
        }
        for (ChatMessage message : messages) {
            switch (message.role()) {
                case USER -> springMessages.add(new UserMessage(message.content()));
                case ASSISTANT -> springMessages.add(new AssistantMessage(message.content()));
            }
        }

        return new Prompt(springMessages);
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
