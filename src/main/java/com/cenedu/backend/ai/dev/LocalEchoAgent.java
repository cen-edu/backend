package com.cenedu.backend.ai.dev;

import com.cenedu.backend.ai.agent.Agent;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 받은 요청을 그대로 되돌려주는 {@code local} 전용 에이전트. LLM 을 부르지 않는다.
 *
 * <p>두 가지 용도로 둔다.
 * <ol>
 *   <li>실제 에이전트가 하나도 없는 동안 디스패처의 성공 경로를 실제로 밟아보기 위해.
 *       지금까지 모든 호출이 {@code AI_AGENT_NOT_FOUND} 로 끝나서 성공 경로가 검증된 적이 없다.</li>
 *   <li>팀원이 자기 에이전트를 만들 때 베껴 쓸 최소 형태로.</li>
 * </ol>
 *
 * <p>{@code text} 와 {@code data} 를 둘 다 채우는 이유는 {@link AgentResponse} 의 두 가지 쓰임
 * (챗봇의 자연어 답변 / 문제 수정의 구조화 결과)을 한눈에 보여주기 위해서다.
 * 진짜 에이전트는 보통 둘 중 하나만 쓴다.
 *
 * <p>입력 내용을 로그와 응답에 그대로 싣는데, 이건 {@code local} 이라서 하는 것이다.
 * 배포되는 코드에서는 사용자 입력 원문을 로그에 남기지 않는다 — 학생 입력과 시험 문항이 그대로
 * 로그 파일로 나가면 정답 유출 정책을 로그가 무너뜨린다.
 */
@Component
@Profile("local")
public class LocalEchoAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(LocalEchoAgent.class);

    @Override
    public AgentKind kind() {
        return AgentKind.ECHO;
    }

    @Override
    public AgentResponse handle(AgentRequest request) {
        log.info("에코 에이전트 수신 — userInput=\"{}\", payloadKeys={}",
                request.userInput(), request.payload().keySet());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", request.kind().name());
        data.put("userId", request.actor().userId());
        data.put("role", request.actor().role().name());
        data.put("userInput", request.userInput());
        data.put("historySize", request.history().size());
        data.put("payload", request.payload());

        String text = """
                에코 응답입니다. 디스패처를 통과해 여기까지 도착했습니다.
                - 호출자: userId=%d, role=%s
                - 입력: %s
                - 히스토리: %d턴
                - payload 키: %s"""
                .formatted(
                        request.actor().userId(),
                        request.actor().role(),
                        request.userInput(),
                        request.history().size(),
                        request.payload().keySet());

        return new AgentResponse(text, data);
    }
}
