package com.cenedu.backend.ai.agent.chat;

import com.cenedu.backend.ai.agent.Agent;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;

import org.springframework.stereotype.Component;

/**
 * 학생이 문제를 푸는 중에 쓰는 개념 챗봇.
 *
 * <p>로직을 두지 않고 {@link ConceptChatEngine} 에 위임만 한다. {@code REVIEW_CHAT} 이 같은 엔진을
 * 쓸 예정이라, 두 에이전트가 같은 파이프라인을 각자 갖는 상황을 만들지 않는다.
 *
 * <p>{@link ConceptChatResult} 의 나머지 값(키워드·근거·토큰)은 측정용이라 응답에 싣지 않는다.
 * 답변 텍스트만 나간다.
 */
@Component
public class SolveChatAgent implements Agent {

    private final ConceptChatEngine engine;

    public SolveChatAgent(ConceptChatEngine engine) {
        this.engine = engine;
    }

    @Override
    public AgentKind kind() {
        return AgentKind.SOLVE_CHAT;
    }

    @Override
    public AgentResponse handle(AgentRequest request) {
        return AgentResponse.ofText(engine.answer(request).text());
    }
}
