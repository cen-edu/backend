package com.cenedu.backend.ai.client;

import com.openai.errors.OpenAIServiceException;
import com.openai.models.completions.CompletionUsage;

import org.springframework.ai.chat.metadata.Usage;

/**
 * OpenAI SDK 원본에서 <b>로그와 측정에 쓸 값만</b> 꺼낸다.
 *
 * <p>여기 있는 이유는 {@code com.openai} 를 참조해도 되는 곳이 {@code ai/client} 뿐이기 때문이다
 * (AGENTS.md 5절, {@code AiClientAccessTest} 가 CI 에서 강제한다). 호출부마다 SDK 예외를 열어
 * 보면 SDK 를 갈아끼울 때 그 호출부를 전부 열어야 하고, 그러라고 공통 Client 를 둔 것이다.
 *
 * <p>Spring AI 의 공통 타입만으로는 부족한 두 가지를 채운다 — 실패 원인을 가르는 데 필요한
 * <b>HTTP 상태·오류 종류</b>와, {@code Usage} 에 자리가 없는 <b>추론 토큰</b>이다.
 */
public final class OpenAiDiagnostics {

    private OpenAiDiagnostics() {
    }

    /**
     * 호출 실패를 <b>로그에 남겨도 되는 형태</b>로 줄인다.
     *
     * <p>남기는 것은 HTTP 상태와 OpenAI 가 붙인 {@code type}·{@code code} 뿐이다. 이 셋이면
     * 인증 실패·정원 초과·요청 거절이 갈린다.
     *
     * <p><b>{@code message} 와 {@code body} 는 남기지 않는다.</b> 거절 사유에는 우리가 보낸
     * 프롬프트 조각이 실려 오는데, 서술형 채점에서 그 조각은 곧 학생 답안이다. 정답·답안 원문을
     * 로그로 내보내지 않는다는 규칙이 여기서 깨지기 가장 쉽다.
     *
     * @return {@code "RateLimitException status=429 type=- code=rate_limit_exceeded"} 꼴.
     *         SDK 예외가 아니면 클래스 이름만
     */
    public static String describe(Throwable failure) {
        if (failure == null) {
            return "-";
        }
        if (!(failure instanceof OpenAIServiceException service)) {
            return failure.getClass().getSimpleName();
        }
        return "%s status=%d type=%s code=%s".formatted(
                service.getClass().getSimpleName(),
                service.statusCode(),
                service.type().orElse("-"),
                service.code().orElse("-"));
    }

    /**
     * 추론 토큰. 공통 {@link Usage} 에 자리가 없어 SDK 원본에서 꺼낸다.
     *
     * <p><b>완성 토큰에 이미 포함된 값이다.</b> 따로 더하면 비용이 두 번 세어진다 — "완성 토큰
     * 중 얼마가 추론이었나" 를 보려고 따로 센다.
     *
     * @return 모델이 내려주지 않으면 {@code null}
     */
    public static Integer reasoningTokens(Usage usage) {
        if (usage == null || !(usage.getNativeUsage() instanceof CompletionUsage completionUsage)) {
            return null;
        }
        return completionUsage.completionTokensDetails()
                .flatMap(CompletionUsage.CompletionTokensDetails::reasoningTokens)
                .map(Long::intValue)
                .orElse(null);
    }
}
