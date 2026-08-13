package com.cenedu.backend.ai.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM 호출 설정. {@code application.yaml} 의 {@code app.ai.client} 를 그대로 받는다.
 *
 * @param apiKey              OpenAI API 키. <b>비어 있어도 기동은 된다.</b> 챗봇을 쓰지 않는 팀원이
 *                            키 없이 개발할 수 있어야 해서, 없을 때는 호출 시점에만 실패한다.
 * @param model               모델 이름. 문자열로 두는 이유는 SDK 의 {@code ChatModel} 상수 목록이
 *                            새 모델보다 늦게 따라오기 때문이다. 설정만 고쳐 모델을 바꿀 수 있어야 한다.
 * @param reasoningEffort     추론 강도. {@code minimal} 이면 추론 토큰을 쓰지 않는다.
 * @param maxCompletionTokens 응답 토큰 상한. GPT-5 계열은 <b>추론 토큰이 여기서 차감</b>되어,
 *                            낮게 잡으면 {@code content} 가 빈 문자열로 잘려 돌아온다.
 * @param timeout             한 번의 호출이 기다리는 최대 시간
 * @param maxRetries          SDK 가 재시도하는 횟수. 무한 재시도를 막는 값이다.
 */
@ConfigurationProperties(prefix = "app.ai.client")
public record OpenAiProperties(
        String apiKey,
        String model,
        String reasoningEffort,
        long maxCompletionTokens,
        Duration timeout,
        int maxRetries
) {
}
