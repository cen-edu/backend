package com.cenedu.backend.ai.agent.chat.loop;

import java.util.List;

/**
 * 도구 루프 한 턴의 결과와 그 턴이 남긴 계측값 전부.
 *
 * <p>답변만 돌려주지 않는 이유는 고정 파이프라인의 {@code ConceptChatResult} 와 같다 — 실패를
 * 귀속시키려면 중간값이 남아야 한다. 다만 귀속의 축이 다르다. 고정 파이프라인은
 * 검색/앵커선택/확장/생성 네 단계였지만, 루프는 <b>어떤 도구를 어떤 인자로 불렀는가</b>가 축이다.
 *
 * @param text              학생에게 갈 답변
 * @param traces            LLM 호출 차수별 계측. 마지막 차수가 답변을 낸 호출이다
 * @param anchorName        첫 {@code get_prereqs} 가 돌려준 hop 0 개념 이름. 없으면 빈 문자열
 * @param deliveredConcepts 본문이 실제로 모델에게 전달된 개념 이름 전부
 * @param toolCallCount     차단된 중복까지 포함한 도구 호출 수 (상한 계산의 분자)
 * @param blockedCount      중복이라 실행하지 않은 호출 수
 * @param cappedOut         상한에 걸려 답변 생성을 강제당했는지 (정지 조건 8 의 대상)
 * @param noEvidence        실행된 도구가 하나도 근거를 못 준 턴인지
 */
public record ConceptLoopResult(
        String text,
        List<LoopTrace> traces,
        String anchorName,
        List<String> deliveredConcepts,
        int toolCallCount,
        int blockedCount,
        boolean cappedOut,
        boolean noEvidence
) {

    public int promptTokens() {
        return traces.stream().mapToInt(trace -> trace.promptTokens() == null ? 0 : trace.promptTokens()).sum();
    }

    public int completionTokens() {
        return traces.stream().mapToInt(trace -> trace.completionTokens() == null ? 0 : trace.completionTokens()).sum();
    }

    /**
     * LLM 호출 한 번의 계측.
     *
     * @param callIndex     호출 차수. 1 부터
     * @param toolsOffered  이 호출에 도구를 실어 보냈는지. 상한 도달 후에는 false 다
     * @param finishReason  {@code stop} 이 아니면 잘렸거나 도구를 부른 것이다
     * @param invocations   이 응답이 요청한 도구 호출들. 비어 있으면 답변을 낸 호출이다
     */
    public record LoopTrace(
            int callIndex,
            boolean toolsOffered,
            Integer promptTokens,
            Integer completionTokens,
            String finishReason,
            List<ToolInvocation> invocations
    ) {
    }

    /**
     * 도구 호출 한 건.
     *
     * @param name        도구 이름
     * @param arguments   모델이 만든 인자 JSON 원문. 중복 판정의 키이자 인자 오류 귀속의 근거다
     * @param resultCount 결과 건수. 파싱할 수 없으면 -1
     * @param outcome     실행됐는지, 왜 실행되지 않았는지
     */
    public record ToolInvocation(
            String name,
            String arguments,
            int resultCount,
            InvocationOutcome outcome
    ) {
    }

    /** 도구 호출이 어떻게 처리됐는가. */
    public enum InvocationOutcome {
        /** 실제로 실행했다. */
        EXECUTED,
        /** 같은 도구를 같은 인자로 이미 불렀다. 실행하지 않고 그 사실을 모델에 알렸다. */
        BLOCKED_DUPLICATE,
        /** 턴당 상한에 걸렸다. 실행하지 않고 답변을 만들라고 알렸다. */
        BLOCKED_CAP,
        /** 이름이 등록된 도구가 아니다. 모델이 지어낸 도구다. */
        UNKNOWN_TOOL,
    }
}
