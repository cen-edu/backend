package com.cenedu.backend.ai.client;

/**
 * 한 useCase 의 모델 설정. 지정하지 않은 값은 {@code app.ai.client} 최상위 설정을 따른다.
 *
 * <p>세 값을 <b>함께</b> 담는다. 하나만 덮어쓸 수 있게 두면 model 만 바꾼 설정이 흔해지는데,
 * Spring AI 는 런타임 옵션을 실을 때 기본 옵션을 물려주지 않아 나머지 둘이 요청에서 사라진다.
 * 여기서 null 을 남겨도 {@link OpenAiProperties#optionsFor} 가 기본값으로 채워 내보낸다.
 *
 * @param model               모델 이름. {@code null} 이면 기본 모델
 * @param reasoningEffort     추론 강도. {@code null} 이면 기본값
 * @param maxCompletionTokens 응답 토큰 상한. {@code null} 이면 기본값
 */
public record LlmModelOptions(String model, String reasoningEffort, Long maxCompletionTokens) {
}
