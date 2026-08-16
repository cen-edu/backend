package com.cenedu.backend.ai.chat.agent;

import java.util.Map;

import com.cenedu.backend.ai.agent.Agent;
import com.cenedu.backend.ai.agent.AgentKind;
import com.cenedu.backend.ai.agent.AgentRequest;
import com.cenedu.backend.ai.agent.AgentResponse;
import com.cenedu.backend.domain.chat.dto.response.ConceptView;

import org.springframework.stereotype.Component;

/**
 * 학생이 문제를 푸는 중에 쓰는 개념 챗봇.
 *
 * <p>로직을 두지 않고 {@link ConceptChatEngine} 에 위임만 한다. {@code REVIEW_CHAT} 이 같은 엔진을
 * 쓸 예정이라, 두 에이전트가 같은 파이프라인을 각자 갖는 상황을 만들지 않는다.
 *
 * <p>{@link ConceptChatResult} 의 나머지 값(키워드·근거·토큰)은 측정용이라 응답에 싣지 않는다.
 * 나가는 것은 답변 텍스트와 <b>이번 턴의 앵커 id</b> 뿐이다.
 *
 * <p><b>앵커를 응답에 싣는 이유(task_24b §0-1).</b> 파이프라인은 상태를 갖지 않으므로 매 턴
 * 키워드로 앵커를 새로 찾는데, 이력에는 대화 첫 개념이 가장 진하게 남아 있어 한 칸 내려간 것이
 * 다음 턴에 원위치한다. 앵커를 서버에 쌓는 대신 <b>응답에 실어 보내고 다음 요청이 되돌려주게</b>
 * 하면 무상태를 유지한 채 그 되감김이 없어진다. 되돌려받는 쪽은
 * {@code AgentRequest.payload()} 의 {@code currentConceptId} 이며 {@link ConceptChatEngine} 이 읽는다.
 *
 * <p>{@code AgentResponse} 는 이동규 소유라 <b>record 에 필드를 더하지 않았다.</b> 이미 있는
 * {@code data} 맵("구조화된 결과가 필요한 경우")에 싣는다.
 *
 * <p>이 경로를 실제로 쓰는 것은 {@code /api/chat} 컨트롤러(task_25)다. task_24b 의 측정은
 * 러너가 같은 왕복을 직접 하므로 여기를 지나지 않는다.
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
        ConceptChatResult result = engine.answer(request);
        ConceptView anchor = result.context().anchor();
        return anchor == null
                ? AgentResponse.ofText(result.text())
                : new AgentResponse(result.text(),
                        Map.of(ConceptChatEngine.PAYLOAD_CURRENT_CONCEPT_ID, anchor.id()));
    }
}
