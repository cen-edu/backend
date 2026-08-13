package com.cenedu.backend.ai.client;

/**
 * LLM 응답. 호출부가 SDK 타입을 모르도록 필요한 값만 꺼내 담는다.
 *
 * <p>{@code text} 는 항상 내용이 있다. 비면 {@link LlmClient} 가 예외를 던지고 여기까지 오지 않는다.
 *
 * @param promptTokens     입력 토큰
 * @param completionTokens 출력 토큰. 추론 토큰을 포함한 값이다.
 * @param reasoningTokens  추론 토큰. 응답에 없으면 0
 */
public record LlmResponse(String text, long promptTokens, long completionTokens, long reasoningTokens) {
}
