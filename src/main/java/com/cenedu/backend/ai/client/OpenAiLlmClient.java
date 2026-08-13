package com.cenedu.backend.ai.client;

import java.util.List;
import java.util.Optional;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;

import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIException;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OpenAI Chat Completions 로 {@link LlmClient} 를 구현한다.
 *
 * <p>SDK 예외를 위로 흘리지 않고 {@link BusinessException} 으로 바꾼다. 도메인 코드가
 * {@code com.openai.errors} 를 import 하기 시작하면 SDK 를 갈아끼울 때 호출부까지 전부 열어야 한다.
 */
@Component
public class OpenAiLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiLlmClient.class);

    private final OpenAIClient client;
    private final OpenAiProperties properties;

    public OpenAiLlmClient(OpenAIClient client, OpenAiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public LlmResponse complete(String systemPrompt, List<ChatMessage> messages, Long seed) {
        ChatCompletionCreateParams params = buildParams(systemPrompt, messages, seed);

        long startedAt = System.nanoTime();
        ChatCompletion completion;
        try {
            completion = client.chat().completions().create(params);
        } catch (OpenAIException e) {
            // 재시도는 SDK 가 max-retries 만큼 이미 끝낸 뒤다. 여기 오면 최종 실패다.
            log.warn("LLM 호출 실패 — model={}, elapsedMs={}", properties.model(), elapsedMs(startedAt), e);
            throw new BusinessException(
                    ErrorCode.AI_CLIENT_CALL_FAILED,
                    "LLM 호출에 실패했습니다: " + e.getMessage());
        }
        long elapsedMs = elapsedMs(startedAt);

        if (completion.choices().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_CLIENT_EMPTY_RESPONSE, "LLM 응답에 선택지가 없습니다.");
        }
        ChatCompletion.Choice choice = completion.choices().get(0);
        String finishReason = choice.finishReason().asString();

        Optional<CompletionUsage> usage = completion.usage();
        long promptTokens = usage.map(CompletionUsage::promptTokens).orElse(0L);
        long completionTokens = usage.map(CompletionUsage::completionTokens).orElse(0L);
        long reasoningTokens = usage.flatMap(CompletionUsage::completionTokensDetails)
                .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                .orElse(0L);

        // 프롬프트 본문과 응답 본문은 남기지 않는다. 학생 입력과 시험 문항이 로그로 나가면
        // 정답 유출 정책이 무너진다. 길이만으로도 대부분의 추적은 된다.
        log.info("LLM 호출 — model={}, elapsedMs={}, promptTokens={}, completionTokens={},"
                        + " reasoningTokens={}, finishReason={}, responseLength={}",
                completion.model(), elapsedMs, promptTokens, completionTokens, reasoningTokens,
                finishReason, choice.message().content().map(String::length).orElse(0));

        String text = choice.message().content().filter(content -> !content.isBlank()).orElseThrow(() ->
                // 빈 문자열을 정상 응답으로 흘리면 챗봇이 빈 말풍선을 띄우고 원인을 못 찾는다.
                // 추론 토큰이 한도를 먹어 잘린 경우가 대표적이라 finishReason 과 토큰을 같이 남긴다.
                new BusinessException(ErrorCode.AI_CLIENT_EMPTY_RESPONSE,
                        "LLM 응답 텍스트가 비어 있습니다 — finishReason=%s, completionTokens=%d, reasoningTokens=%d, maxCompletionTokens=%d"
                                .formatted(finishReason, completionTokens, reasoningTokens,
                                        properties.maxCompletionTokens())));

        return new LlmResponse(text, promptTokens, completionTokens, reasoningTokens);
    }

    private ChatCompletionCreateParams buildParams(String systemPrompt, List<ChatMessage> messages, Long seed) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(properties.model())
                .reasoningEffort(ReasoningEffort.of(properties.reasoningEffort()))
                .maxCompletionTokens(properties.maxCompletionTokens());

        if (seed != null) {
            // SDK 의 seed() 는 @Deprecated 라 프로덕션 코드에 억제 애너테이션이 붙는다.
            // 본문 프로퍼티로 직접 넣어 그 자국을 남기지 않는다. 보내는 JSON 은 같다.
            builder.putAdditionalBodyProperty("seed", JsonValue.from(seed));
        }

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.addSystemMessage(systemPrompt);
        }
        for (ChatMessage message : messages) {
            switch (message.role()) {
                case USER -> builder.addUserMessage(message.content());
                case ASSISTANT -> builder.addAssistantMessage(message.content());
            }
        }

        return builder.build();
    }

    private static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
