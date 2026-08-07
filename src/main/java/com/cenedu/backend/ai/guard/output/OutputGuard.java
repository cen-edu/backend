package com.cenedu.backend.ai.guard.output;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.ai.guard.GuardDecision;

/**
 * 에이전트 응답을 학생·교사에게 돌려주기 전에 검사한다. 구현체를 {@code @Component} 로 등록한다.
 *
 * <p>완성된 응답을 통째로 받는다. 이게 {@link AgentResponse} 를 스트리밍 타입으로 두지 않은 이유다.
 *
 * <p>같은 질문이라도 상황에 따라 판정이 달라야 해서 {@code request} 를 함께 받는다.
 * 예를 들어 정답을 알려주는 응답은 풀이 중 챗봇에서는 막아야 하지만 해설 화면에서는 막을 이유가 없고,
 * 종합평가 풀이 중에는 가장 엄격해야 한다.
 */
public interface OutputGuard {

    GuardDecision inspect(AgentRequest request, AgentResponse response);
}
