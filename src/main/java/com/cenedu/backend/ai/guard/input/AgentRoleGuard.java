package com.cenedu.backend.ai.guard.input;

import com.cenedu.backend.ai.agent.Actor;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.guard.GuardDecision;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 요청자의 역할이 호출하려는 에이전트에 허용되는지 검사한다.
 *
 * <p>역할 검사는 요청 내용보다 먼저 판단할 수 있고, 권한이 없는 요청을 다른 입력 검사에
 * 노출할 이유가 없으므로 입력 가드 중 가장 먼저 실행한다.
 *
 * <p>새 {@link AgentKind} 를 추가하고 이 정책에 등록하지 않으면 기본적으로 차단한다.
 * 허용 목록 누락이 권한 우회로 이어지는 것보다 명시적으로 실패하는 편이 안전하다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AgentRoleGuard implements InputGuard {

    static final String REASON_CODE = "AGENT_ROLE_NOT_ALLOWED";

    private static final Map<AgentKind, Set<Actor.Role>> ALLOWED_ROLES = allowedRoles();

    @Override
    public GuardDecision inspect(AgentRequest request) {
        Set<Actor.Role> allowedRoles = ALLOWED_ROLES.get(request.kind());
        if (allowedRoles != null && allowedRoles.contains(request.actor().role())) {
            return GuardDecision.allow();
        }

        return GuardDecision.block(REASON_CODE, "사용자 역할에 허용되지 않은 에이전트 요청입니다.");
    }

    private static Map<AgentKind, Set<Actor.Role>> allowedRoles() {
        Map<AgentKind, Set<Actor.Role>> roles = new EnumMap<>(AgentKind.class);

        // local 프로파일 전용 개발 도구이므로 두 역할 모두 성공 경로를 확인할 수 있게 한다.
        roles.put(AgentKind.ECHO, EnumSet.allOf(Actor.Role.class));

        roles.put(AgentKind.PROBLEM_EDIT, EnumSet.of(Actor.Role.TEACHER));
        roles.put(AgentKind.SOLVE_CHAT, EnumSet.of(Actor.Role.STUDENT));
        roles.put(AgentKind.REVIEW_CHAT, EnumSet.of(Actor.Role.STUDENT));

        return Map.copyOf(roles);
    }
}
