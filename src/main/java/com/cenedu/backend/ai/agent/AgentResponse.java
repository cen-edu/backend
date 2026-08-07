package com.cenedu.backend.ai.agent;

import java.util.Map;

/**
 * 에이전트 호출 결과. <b>완성된 응답</b>이며 스트리밍 타입이 아니다.
 *
 * <p>출력 가드레일이 전체 응답을 보고 판단해야 해서 의도적으로 이렇게 뒀다. 토큰을 흘려보내면
 * 이미 내보낸 내용을 되돌릴 수 없다. 에이전트가 내부에서 LLM 스트리밍을 쓰는 것은 자유이고,
 * 모아서 이 타입으로 돌려주기만 하면 된다.
 *
 * <p>체감 지연은 프론트에서 완성 응답을 타이핑 효과로 뿌려 흡수한다.
 *
 * @param text 챗봇처럼 자연어 답변이 결과인 경우. 아니면 {@code null}
 * @param data 문제 수정처럼 구조화된 결과가 필요한 경우. 아니면 빈 맵
 */
public record AgentResponse(String text, Map<String, Object> data) {

    public AgentResponse {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static AgentResponse ofText(String text) {
        return new AgentResponse(text, Map.of());
    }

    public static AgentResponse ofData(Map<String, Object> data) {
        return new AgentResponse(null, data);
    }
}
