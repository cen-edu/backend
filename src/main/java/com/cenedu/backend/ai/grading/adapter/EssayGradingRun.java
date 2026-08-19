package com.cenedu.backend.ai.grading.adapter;

import java.util.Map;

import com.cenedu.backend.domain.grading.port.EssayGradingResult;

/**
 * 채점 결과에 <b>측정값</b>을 붙인 것. 도메인 계약({@link EssayGradingResult})은 이걸 모른다.
 *
 * <p>Port 로 내보내지 않는 이유는 도메인이 쓸 값이 아니기 때문이다. 턴 수·토큰·도구 상태는
 * 단계 4~5 의 대조군 비교에만 쓰인다. 측정 러너는 Port 가 아니라 Adapter 를 직접 부른다.
 */
public record EssayGradingRun(EssayGradingResult result, Trace trace) {

    /**
     * 한 칸 채점의 관측값.
     *
     * @param toolsOffered      도구를 실은 호출인지. 단계 4 의 A 군·B 군을 가른다
     * @param modelCalls        LLM 호출 차수
     * @param toolCalls         도구 호출 횟수
     * @param toolStatusCounts  도구가 돌려준 {@code status} 별 횟수. 인자는 담지 않는다(D11).
     *                          {@code UNREADABLE} 은 {@code UNREADABLE:IMPLICIT_MULT} 처럼
     *                          사유까지 붙은 키로 센다 — D9 의 분자가 거기서 나온다
     * @param droppedItems      우리가 준 목록에 없어서 버린 판정 수
     * @param malformedOutputs  약속한 JSON 으로 읽지 못한 최종 응답 수
     * @param reasoningTokens   추론 토큰. 완성 토큰에 이미 포함된 값이라 <b>더하지 않는다</b>.
     *                          모델이 안 내려주면 {@code null}
     * @param elapsedMillis     칸당 소요. 단계 6 이 "교사가 기다릴 만한가" 를 이 값으로 본다
     */
    public record Trace(boolean toolsOffered, int modelCalls, int toolCalls,
                        Map<String, Integer> toolStatusCounts, int droppedItems,
                        int malformedOutputs, Integer promptTokens, Integer completionTokens,
                        Integer reasoningTokens, long elapsedMillis) {
    }
}
