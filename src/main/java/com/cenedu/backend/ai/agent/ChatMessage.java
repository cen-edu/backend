package com.cenedu.backend.ai.agent;

/**
 * 대화 한 턴. 에이전트는 히스토리를 직접 저장하지 않고 {@link AgentRequest#history()} 로 받는다.
 *
 * <p>저장 주체를 한 곳으로 모아 두어야 나중에 여러 턴에 걸친 우회 시도를 가드레일이 볼 수 있다.
 * 에이전트마다 따로 저장하면 그 판단을 할 수 없다.
 */
public record ChatMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT,
    }

    public static ChatMessage user(String content) {
        return new ChatMessage(Role.USER, content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage(Role.ASSISTANT, content);
    }
}
