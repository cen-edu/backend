package com.cenedu.backend.ai.agent;

/**
 * 에이전트 하나. 구현체는 {@code @Component} 로 등록하면 디스패처가 기동 시 자동으로 찾는다.
 *
 * <p>구현할 때 지킬 것:
 * <ol>
 *   <li>HTTP 컨트롤러가 아니라 이 인터페이스의 구현체로 만든다. 인증·DB 조회를 안에 섞지 않는다.</li>
 *   <li>사용자 식별 정보는 {@link AgentRequest#actor()} 로 받는다. 토큰을 직접 파싱하지 않는다.</li>
 *   <li>LLM 스트리밍 응답을 그대로 흘려보내지 않는다. 모아서 {@link AgentResponse} 로 돌려준다.</li>
 *   <li>대화 히스토리를 직접 저장하지 않는다. {@link AgentRequest#history()} 로 받는다.</li>
 * </ol>
 *
 * <p>프롬프트 내용, 모델 선택, 내부 RAG 구성은 전부 각 구현체의 몫이다. 디스패처는 관여하지 않는다.
 */
public interface Agent {

    /** 이 구현체가 담당하는 에이전트. 같은 값을 두 구현체가 쓰면 기동 시점에 실패한다. */
    AgentKind kind();

    /**
     * 요청을 처리해 완성된 응답을 돌려준다.
     *
     * <p>이 메서드는 가드레일을 통과한 뒤에만 호출된다. 구현체가 입력 검증을 다시 하지 않아도 된다.
     */
    AgentResponse handle(AgentRequest request);
}
