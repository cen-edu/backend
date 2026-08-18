package com.cenedu.backend.ai.problem.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentPayload;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentResultEnvelope;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 사용자 문제 수정 프롬프트를 구조화된 한 턴 결과로 변환하는 Dispatcher 전용 Agent다. */
@Component
public class ProblemEditAgent implements Agent {
    public static final String REQUEST_KEY = "problemEditContext";

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final ProblemEditPromptFactory promptFactory;

    public ProblemEditAgent(LlmClient llmClient, ObjectProvider<ObjectMapper> objectMapper,
                            ProblemEditPromptFactory promptFactory) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
        this.promptFactory = promptFactory;
    }

    @Override
    public AgentKind kind() {
        return AgentKind.PROBLEM_EDIT;
    }

    /** DB를 사용하지 않고 payload와 사용자 입력만으로 구조화 결과를 반환한다. */
    @Override
    public AgentResponse handle(AgentRequest request) {
        try {
            ProblemEditAgentPayload payload = objectMapper.convertValue(
                    request.payload().get(REQUEST_KEY), ProblemEditAgentPayload.class);
            String response = llmClient.complete(promptFactory.create(payload),
                    List.of(ChatMessage.user(request.userInput()))).text();
            ProblemEditAgentResultEnvelope envelope = objectMapper.readValue(
                    response, ProblemEditAgentResultEnvelope.class);
            return AgentResponse.ofData(Map.of(
                    ProblemEditAgentResultEnvelope.RESPONSE_KEY, envelope.problemEditResult()));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("문제 수정 Agent 응답을 해석할 수 없습니다.", exception);
        }
    }
}
