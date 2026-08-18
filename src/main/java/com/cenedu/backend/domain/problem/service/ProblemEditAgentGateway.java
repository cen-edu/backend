package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.dispatcher.AgentDispatcher;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Problem 도메인이 사용자 수정 프롬프트를 Dispatcher로만 보내도록 고정하는 Gateway다. */
@Component
public class ProblemEditAgentGateway {
    private static final String REQUEST_KEY = "problemEditContext";
    private final AgentDispatcher dispatcher;
    private final ObjectMapper objectMapper;

    public ProblemEditAgentGateway(AgentDispatcher dispatcher, ObjectProvider<ObjectMapper> objectMapper) {
        this.dispatcher = dispatcher;
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
    }

    /** 교사 입력과 검증된 문제 컨텍스트를 PROBLEM_EDIT Agent에 전달한다. */
    public ProblemEditConversationResult handle(long teacherId, String userInput,
                                                 List<ChatMessage> history,
                                                 ProblemEditAgentPayload payload) {
        AgentResponse response = dispatcher.dispatch(new AgentRequest(AgentKind.PROBLEM_EDIT,
                new Actor(teacherId, Actor.Role.TEACHER), userInput, history,
                Map.of(REQUEST_KEY, payload)));
        Object value = response.data().get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        if (value == null) throw new IllegalStateException("문제 수정 Agent 결과가 없습니다.");
        return objectMapper.convertValue(value, ProblemEditConversationResult.class);
    }
}
