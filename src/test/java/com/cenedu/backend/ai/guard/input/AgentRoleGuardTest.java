package com.cenedu.backend.ai.guard.input;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AgentRoleGuardTest {

    private final AgentRoleGuard guard = new AgentRoleGuard();

    @ParameterizedTest(name = "{0} 역할은 {1} 호출이 허용된다")
    @CsvSource({
            "TEACHER, PROBLEM_EDIT",
            "STUDENT, SOLVE_CHAT",
            "STUDENT, REVIEW_CHAT",
            "TEACHER, ECHO",
            "STUDENT, ECHO"
    })
    @DisplayName("역할에 맞는 에이전트 호출은 통과시킨다")
    void allowsAgentForExpectedRole(Actor.Role role, AgentKind kind) {
        GuardDecision decision = guard.inspect(request(role, kind));

        assertThat(decision).isEqualTo(GuardDecision.allow());
    }

    @ParameterizedTest(name = "{0} 역할은 {1} 호출이 차단된다")
    @CsvSource({
            "STUDENT, PROBLEM_EDIT",
            "TEACHER, SOLVE_CHAT",
            "TEACHER, REVIEW_CHAT"
    })
    @DisplayName("역할에 맞지 않는 에이전트 호출은 차단한다")
    void blocksAgentForUnexpectedRole(Actor.Role role, AgentKind kind) {
        GuardDecision decision = guard.inspect(request(role, kind));

        assertThat(decision.blocked()).isTrue();
        assertThat(decision.reasonCode()).isEqualTo(AgentRoleGuard.REASON_CODE);
        assertThat(decision.message()).doesNotContain(role.name(), kind.name());
    }

    private static AgentRequest request(Actor.Role role, AgentKind kind) {
        return AgentRequest.of(kind, new Actor(1L, role), "검사 대상 원문", Map.of());
    }
}
