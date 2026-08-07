package com.cenedu.backend.ai.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 스프링이 실제로 조립한 디스패처로 성공 경로를 한 번 밟아본다.
 *
 * <p>{@link AgentDispatcherTest} 는 디스패처를 직접 생성해 분기만 검증한다. 여기서 확인하는 건
 * 그게 아니라 <b>배선</b>이다 — {@code @Component} 로 등록한 에이전트를 {@code ObjectProvider}
 * 가 실제로 주워 오는지, {@code @Profile("local")} 이 걸린 빈이 뜨는지.
 * 지금까지 모든 호출이 {@code AI_AGENT_NOT_FOUND} 로 끝나서 이 경로가 검증된 적이 없었다.
 *
 * <p>{@code @SpringBootTest} 라 전체 컨텍스트가 뜨고, JPA 때문에 살아 있는 DB 가 필요하다
 * ({@code docker compose up -d}). {@code BackendApplicationTests} 와 같은 조건이다.
 */
@SpringBootTest
@ActiveProfiles("local")
class AgentDispatcherContextTest {

    @Autowired
    private AgentDispatcher dispatcher;

    @Test
    @DisplayName("local 프로파일에서 에코 에이전트가 등록되고 요청이 그대로 되돌아온다")
    void echoAgentIsWiredAndReturnsTheRequest() {
        AgentRequest request = AgentRequest.of(
                AgentKind.ECHO,
                new Actor(7L, Actor.Role.STUDENT),
                "이 문제 어떻게 시작해요?",
                Map.of("problemId", 42L));

        AgentResponse response = dispatcher.dispatch(request);

        assertThat(response.text()).contains("이 문제 어떻게 시작해요?");
        assertThat(response.data())
                .containsEntry("userId", 7L)
                .containsEntry("role", "STUDENT")
                .containsEntry("userInput", "이 문제 어떻게 시작해요?")
                .containsEntry("payload", Map.of("problemId", 42L));
    }

    @Test
    @DisplayName("아직 구현체가 없는 서피스를 부르면 AI_AGENT_NOT_FOUND 가 난다")
    void realSurfacesAreStillUnimplemented() {
        AgentRequest request = AgentRequest.of(
                AgentKind.PROBLEM_EDIT,
                new Actor(1L, Actor.Role.TEACHER),
                "풀이를 더 잘게 나눠줘",
                Map.of());

        assertThatThrownBy(() -> dispatcher.dispatch(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_AGENT_NOT_FOUND);
    }
}
