package com.cenedu.backend.ai.guard.input;

import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;

/**
 * 에이전트를 부르기 전에 요청을 검사한다. 구현체를 {@code @Component} 로 등록하면 자동으로 끼어든다.
 *
 * <p>지금은 구현체가 없어서 모든 요청이 그대로 통과한다. 자리만 잡아 둔 것이고, 나중에 여기에
 * 역할·인젝션·범위·개인정보 검사를 채운다. 채워 넣을 때 에이전트 코드는 건드리지 않는다.
 *
 * <p>여러 개를 등록하면 {@code @Order} 순서대로 돌고, 하나라도 막으면 거기서 끝난다.
 */
public interface InputGuard {

    GuardDecision inspect(AgentRequest request);
}
