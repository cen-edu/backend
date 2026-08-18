package com.cenedu.backend.ai.client;

import java.time.Duration;
import java.util.Map;

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
 * @param useCases            목적별 모델 설정. 비어 있으면 모든 호출이 위 기본값을 쓴다.
 */
@ConfigurationProperties(prefix = "app.ai.client")
public record OpenAiProperties(
        String apiKey,
        String model,
        String reasoningEffort,
        long maxCompletionTokens,
        Duration timeout,
        int maxRetries,
        Map<LlmUseCase, LlmModelOptions> useCases
) {

    public OpenAiProperties {
        // EnumMap 으로 감싸지 않는다. 빈 Map 을 넘기면 키 타입을 못 정해 예외가 난다.
        useCases = useCases == null || useCases.isEmpty() ? Map.of() : Map.copyOf(useCases);
    }

    /**
     * 요청에 실을 모델 설정을 확정한다. useCase 설정에 빠진 값은 기본값으로 채운다.
     *
     * <p>세 값을 모두 채워 돌려주는 것이 이 메서드의 요점이다. 부분만 돌려주면 호출부가
     * 나머지를 안 싣고, 그 호출만 상한도 추론 강도도 없이 나간다.
     */
    public LlmModelOptions optionsFor(LlmUseCase useCase) {
        LlmModelOptions configured = useCase == null ? null : useCases.get(useCase);
        if (configured == null) {
            return new LlmModelOptions(model, reasoningEffort, maxCompletionTokens);
        }
        return new LlmModelOptions(
                configured.model() != null ? configured.model() : model,
                configured.reasoningEffort() != null ? configured.reasoningEffort() : reasoningEffort,
                configured.maxCompletionTokens() != null
                        ? configured.maxCompletionTokens() : maxCompletionTokens);
    }
}
