package com.cenedu.backend.ai.dispatcher;

import com.cenedu.backend.ai.agent.Agent;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.input.InputGuard;
import com.cenedu.backend.ai.guard.output.OutputGuard;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 모든 에이전트 호출의 유일한 진입점.
 *
 * <p>도메인 서비스는 LLM 클라이언트를 직접 부르지 않고 {@link #dispatch(AgentRequest)} 만 부른다.
 * 이 규칙은 {@code AiClientAccessTest} 가 CI 에서 강제한다.
 *
 * <p>입력 역할 검사는 구현되어 있으며, 나머지 입력 검사와 출력 검사는 구현체가 등록될 때까지
 * 그대로 통과한다. 가드레일을 추가할 때 에이전트나 도메인 코드를 고치지 않아도 된다.
 *
 * <p>왜 한 곳으로 모으는가: 챗봇이 여러 개로 늘 때 가드레일을 각자 구현하면 서로 다른 안전
 * 수준이 생긴다. 예외 경로를 하나 열어 두면 그 경로만 무방비가 되고, 나중에 누가 거기에
 * 사용자 입력을 얹는 순간 원칙이 무너진다.
 */
@Component
public class AgentDispatcher {

    private static final Logger log = LoggerFactory.getLogger(AgentDispatcher.class);

    private final Map<AgentKind, Agent> agents;
    private final List<InputGuard> inputGuards;
    private final List<OutputGuard> outputGuards;

    /**
     * {@code ObjectProvider} 를 쓰는 이유: 아직 등록된 에이전트도 가드레일도 없다.
     * {@code List<Agent>} 로 직접 주입받으면 후보 빈이 0개일 때 기동이 실패한다.
     */
    @Autowired
    public AgentDispatcher(
            ObjectProvider<Agent> agents,
            ObjectProvider<InputGuard> inputGuards,
            ObjectProvider<OutputGuard> outputGuards
    ) {
        this(agents.orderedStream().toList(),
                inputGuards.orderedStream().toList(),
                outputGuards.orderedStream().toList());
    }

    /** 테스트에서 직접 조립할 때 쓴다. */
    public AgentDispatcher(List<Agent> agents, List<InputGuard> inputGuards, List<OutputGuard> outputGuards) {
        this.agents = index(agents);
        this.inputGuards = List.copyOf(inputGuards);
        this.outputGuards = List.copyOf(outputGuards);
        log.info("AgentDispatcher 준비 완료 — 에이전트 {}개, 입력 가드 {}개, 출력 가드 {}개",
                this.agents.size(), this.inputGuards.size(), this.outputGuards.size());
    }

    /**
     * 입력 가드레일 → 에이전트 → 출력 가드레일 순으로 처리하고 완성된 응답을 돌려준다.
     *
     * <p>이 호출 구간의 모든 로그에는 {@code traceId} 가 붙는다. 자세한 건 {@link TraceId} 참고.
     *
     * @throws BusinessException 가드레일이 막았거나 해당 에이전트가 등록되지 않은 경우
     */
    public AgentResponse dispatch(AgentRequest request) {
        try (TraceId ignored = TraceId.ensure()) {
            return doDispatch(request);
        }
    }

    private AgentResponse doDispatch(AgentRequest request) {
        // 입력 원문은 남기지 않는다. 학생 입력과 시험 문항이 그대로 로그로 나가면
        // 정답 유출 정책을 로그 파일이 무너뜨린다. 길이만으로도 대부분의 추적은 된다.
        log.info("에이전트 호출 — agent={}, userId={}, role={}, inputLength={}, historySize={}",
                request.kind(),
                request.actor().userId(),
                request.actor().role(),
                request.userInput() == null ? 0 : request.userInput().length(),
                request.history().size());

        for (InputGuard guard : inputGuards) {
            GuardDecision decision = guard.inspect(request);
            if (decision.blocked()) {
                log.info("입력 차단 — agent={}, guard={}, reason={}",
                        request.kind(), guard.getClass().getSimpleName(), decision.reasonCode());
                throw new BusinessException(ErrorCode.AI_REQUEST_BLOCKED, decision.message());
            }
        }

        // 권한 없는 사용자가 에이전트 등록 여부를 탐색하지 못하도록 입력 검사 뒤에 조회한다.
        Agent agent = agentFor(request.kind());
        AgentResponse response = agent.handle(request);

        for (OutputGuard guard : outputGuards) {
            GuardDecision decision = guard.inspect(request, response);
            if (decision.blocked()) {
                log.warn("출력 차단 — agent={}, guard={}, reason={}",
                        request.kind(), guard.getClass().getSimpleName(), decision.reasonCode());
                throw new BusinessException(ErrorCode.AI_RESPONSE_BLOCKED, decision.message());
            }
        }

        return response;
    }

    private Agent agentFor(AgentKind kind) {
        Agent agent = agents.get(kind);
        if (agent == null) {
            throw new BusinessException(ErrorCode.AI_AGENT_NOT_FOUND, "등록되지 않은 에이전트입니다: " + kind);
        }
        return agent;
    }

    /** 같은 {@link AgentKind} 를 두 구현체가 쓰면 조용히 덮어쓰지 않고 기동 시점에 실패시킨다. */
    private static Map<AgentKind, Agent> index(List<Agent> agents) {
        Map<AgentKind, Agent> byKind = new EnumMap<>(AgentKind.class);
        for (Agent agent : agents) {
            Agent previous = byKind.put(agent.kind(), agent);
            if (previous != null) {
                throw new IllegalStateException("에이전트 %s 를 두 구현체가 담당합니다: %s, %s".formatted(
                        agent.kind(),
                        previous.getClass().getName(),
                        agent.getClass().getName()));
            }
        }
        return Collections.unmodifiableMap(byKind);
    }
}
