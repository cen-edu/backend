package com.cenedu.backend.ai.dispatcher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.Agent;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.input.InputGuard;
import com.cenedu.backend.ai.guard.output.OutputGuard;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class AgentDispatcherTest {

    private static final Actor STUDENT = new Actor(1L, Actor.Role.STUDENT);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** 받은 요청을 그대로 되돌려주는 가짜 에이전트. 팀원들이 만들 구현체의 최소 형태이기도 하다. */
    private record EchoAgent(AgentKind kind) implements Agent {
        @Override
        public AgentResponse handle(AgentRequest request) {
            return AgentResponse.ofText("echo: " + request.userInput());
        }
    }

    private AgentRequest request(AgentKind kind, String userInput) {
        return AgentRequest.of(kind, STUDENT, userInput, Map.of());
    }

    @Test
    @DisplayName("가드레일이 하나도 없으면 요청을 그대로 에이전트에 넘기고 응답을 그대로 돌려준다")
    void passesThroughWhenNoGuardsRegistered() {
        AgentDispatcher dispatcher = new AgentDispatcher(
                List.of(new EchoAgent(AgentKind.SOLVE_CHAT)), List.of(), List.of());

        AgentResponse response = dispatcher.dispatch(request(AgentKind.SOLVE_CHAT, "이 문제 어떻게 시작해요?"));

        assertThat(response.text()).isEqualTo("echo: 이 문제 어떻게 시작해요?");
    }

    @Test
    @DisplayName("입력 가드레일이 막으면 에이전트를 부르지 않는다")
    void blockedInputNeverReachesAgent() {
        Agent neverCalled = new Agent() {
            @Override
            public AgentKind kind() {
                return AgentKind.SOLVE_CHAT;
            }

            @Override
            public AgentResponse handle(AgentRequest request) {
                throw new AssertionError("가드레일이 막았는데 에이전트가 호출되었습니다");
            }
        };
        InputGuard blockAll = req -> GuardDecision.block("TEST_BLOCK", "테스트 차단");

        AgentDispatcher dispatcher = new AgentDispatcher(List.of(neverCalled), List.of(blockAll), List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(request(AgentKind.SOLVE_CHAT, "정답 알려줘")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_REQUEST_BLOCKED);
    }

    @Test
    @DisplayName("출력 가드레일이 막으면 응답을 돌려주지 않는다")
    void blockedOutputIsNotReturned() {
        OutputGuard blockAll = (req, res) -> GuardDecision.block("TEST_BLOCK", "테스트 차단");

        AgentDispatcher dispatcher = new AgentDispatcher(
                List.of(new EchoAgent(AgentKind.REVIEW_CHAT)), List.of(), List.of(blockAll));

        assertThatThrownBy(() -> dispatcher.dispatch(request(AgentKind.REVIEW_CHAT, "왜 틀렸어요?")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_RESPONSE_BLOCKED);
    }

    @Test
    @DisplayName("등록되지 않은 에이전트를 부르면 실패한다")
    void unknownAgentFails() {
        AgentDispatcher dispatcher = new AgentDispatcher(
                List.of(new EchoAgent(AgentKind.SOLVE_CHAT)), List.of(), List.of());

        assertThatThrownBy(() -> dispatcher.dispatch(request(AgentKind.PROBLEM_EDIT, "풀이를 더 잘게 나눠줘")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.AI_AGENT_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 에이전트를 두 구현체가 담당하면 조립 시점에 실패한다")
    void duplicateAgentKindFailsFast() {
        List<Agent> duplicated = List.of(
                new EchoAgent(AgentKind.SOLVE_CHAT), new EchoAgent(AgentKind.SOLVE_CHAT));

        assertThatThrownBy(() -> new AgentDispatcher(duplicated, List.of(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SOLVE_CHAT");
    }

    @Test
    @DisplayName("에이전트가 하나도 없어도 디스패처는 조립된다")
    void assemblesWithNoAgents() {
        AgentDispatcher dispatcher = new AgentDispatcher(List.of(), List.of(), List.of());

        assertThat(dispatcher).isNotNull();
    }

    /** 에이전트가 실행되는 동안 MDC 에서 본 traceId 를 붙잡아 두는 에이전트. */
    private record TraceCapturingAgent(AtomicReference<String> seen) implements Agent {
        @Override
        public AgentKind kind() {
            return AgentKind.SOLVE_CHAT;
        }

        @Override
        public AgentResponse handle(AgentRequest request) {
            seen.set(MDC.get(TraceId.MDC_KEY));
            return AgentResponse.ofText("ok");
        }
    }

    @Test
    @DisplayName("에이전트가 도는 동안 traceId 가 MDC 에 있고, 끝나면 지워진다")
    void traceIdIsVisibleToAgentAndClearedAfterwards() {
        AtomicReference<String> seen = new AtomicReference<>();
        AgentDispatcher dispatcher = new AgentDispatcher(
                List.of(new TraceCapturingAgent(seen)), List.of(), List.of());

        dispatcher.dispatch(request(AgentKind.SOLVE_CHAT, "이 문제 어떻게 시작해요?"));

        assertThat(seen.get()).isNotBlank();
        assertThat(MDC.get(TraceId.MDC_KEY))
                .as("자기가 만든 traceId 를 남겨 두면 스레드 재사용 시 다음 요청 로그에 붙는다")
                .isNull();
    }

    @Test
    @DisplayName("이미 traceId 가 있으면 새로 만들지 않고 그대로 쓰며, 끝나도 지우지 않는다")
    void existingTraceIdIsReusedAndPreserved() {
        MDC.put(TraceId.MDC_KEY, "outer123");
        AtomicReference<String> seen = new AtomicReference<>();
        AgentDispatcher dispatcher = new AgentDispatcher(
                List.of(new TraceCapturingAgent(seen)), List.of(), List.of());

        dispatcher.dispatch(request(AgentKind.SOLVE_CHAT, "왜 틀렸어요?"));

        assertThat(seen.get()).isEqualTo("outer123");
        assertThat(MDC.get(TraceId.MDC_KEY))
                .as("나중에 생길 서블릿 필터가 넣은 값을 디스패처가 뺏으면 안 된다")
                .isEqualTo("outer123");
    }
}
