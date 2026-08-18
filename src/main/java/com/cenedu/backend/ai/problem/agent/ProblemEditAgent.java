package com.cenedu.backend.ai.problem.agent;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentPayload;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditAgentResultEnvelope;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditConversationResult;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
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
            String response = llmClient.completeStructured(promptFactory.create(payload),
                    List.of(ChatMessage.user(request.userInput())),
                    ProblemStructuredOutputSchemas.EDIT_TURN).text();
            ProblemEditAgentResultEnvelope envelope = objectMapper.readValue(
                    response, ProblemEditAgentResultEnvelope.class);
            ProblemEditConversationResult normalized = normalizeTargets(
                    payload, envelope.problemEditResult());
            return AgentResponse.ofData(Map.of(
                    ProblemEditAgentResultEnvelope.RESPONSE_KEY, normalized));
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("문제 수정 Agent 응답을 해석할 수 없습니다.", exception);
        }
    }

    /** UI가 선택한 대상을 모델이 임의의 논리 키로 바꾸지 못하게 한다. */
    private ProblemEditConversationResult normalizeTargets(
            ProblemEditAgentPayload payload,
            ProblemEditConversationResult result
    ) {
        List<ProblemEditInstruction> deltas = result.instructionDeltas() == null
                ? List.of() : result.instructionDeltas();
        var selected = payload.selectedTarget();
        List<ProblemEditInstruction> normalized = deltas.stream().map(instruction -> {
            if (selected != null) {
                return new ProblemEditInstruction(selected.targetType(), selected.targetKey(),
                        instruction.changeNature(), instruction.instruction());
            }
            String targetKey = keyed(instruction.targetType())
                    ? instruction.targetKey() : null;
            return new ProblemEditInstruction(instruction.targetType(), targetKey,
                    instruction.changeNature(), instruction.instruction());
        }).toList();
        return new ProblemEditConversationResult(
                result.action(), normalized, result.assistantMessage());
    }

    private boolean keyed(EditTargetType type) {
        return switch (type) {
            case CONTENT_BLOCK, CHOICE, STEP, ANSWER_UNIT, RUBRIC_ITEM, ASSET -> true;
            case QUESTION_BODY, EXPLANATION, LEARNING_GUIDE,
                    QUESTION_TYPE, DIFFICULTY, WHOLE_QUESTION -> false;
        };
    }
}
