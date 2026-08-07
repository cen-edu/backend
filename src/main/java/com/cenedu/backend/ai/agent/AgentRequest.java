package com.cenedu.backend.ai.agent;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 에이전트 호출 입력. 도메인 서비스가 만들어 {@code AgentDispatcher} 에 넘긴다.
 *
 * <p>{@link #payload()} 를 일부러 느슨하게(문자열 키 맵) 뒀다. 문제·학습지 데이터의 정본 스키마는
 * 각 도메인이 정하는 것이고, 그게 정해지기 전에 이 자리에 타입을 박으면 나중에 전부 고쳐야 한다.
 * 스키마가 굳으면 그때 에이전트별 전용 타입으로 바꾼다.
 *
 * @param kind      호출할 에이전트
 * @param actor     요청을 일으킨 사용자
 * @param userInput 사용자가 실제로 입력한 문장. 가드레일이 검사하는 대상이라 payload 에 섞지 않는다.
 * @param history   지금까지의 대화. 단발성 호출이면 빈 리스트
 * @param payload   에이전트가 필요로 하는 부가 컨텍스트 (문항 id, 학습지 유형, 원본 문제 등)
 */
public record AgentRequest(
        AgentKind kind,
        Actor actor,
        String userInput,
        List<ChatMessage> history,
        Map<String, Object> payload
) {

    public AgentRequest {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(actor, "actor");
        history = history == null ? List.of() : List.copyOf(history);
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    /** 대화 히스토리가 없는 단발성 호출 (문제 수정 등). */
    public static AgentRequest of(AgentKind kind, Actor actor, String userInput, Map<String, Object> payload) {
        return new AgentRequest(kind, actor, userInput, List.of(), payload);
    }
}
