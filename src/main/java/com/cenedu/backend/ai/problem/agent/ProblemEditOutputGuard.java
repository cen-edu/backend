package com.cenedu.backend.ai.problem.agent;

import java.util.Locale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cenedu.backend.ai.agent.*;
import com.cenedu.backend.ai.guard.GuardDecision;
import com.cenedu.backend.ai.guard.output.OutputGuard;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** PROBLEM_EDIT 구조화 응답의 허용 action과 민감 내용 노출을 검사한다. */
@Component
public class ProblemEditOutputGuard implements OutputGuard {
    private final ObjectMapper objectMapper;

    public ProblemEditOutputGuard(ObjectProvider<ObjectMapper> objectMapper) {
        this.objectMapper = objectMapper.getIfAvailable(ObjectMapper::new);
    }

    @Override
    public GuardDecision inspect(AgentRequest request, AgentResponse response) {
        if (request.kind() != AgentKind.PROBLEM_EDIT) return GuardDecision.allow();
        Object value = response.data().get(ProblemEditAgentResultEnvelope.RESPONSE_KEY);
        if (value == null) return GuardDecision.block("PROBLEM_EDIT_RESULT_MISSING", "문제 수정 결과가 없습니다.");
        try {
            ProblemEditConversationResult result = objectMapper.convertValue(value, ProblemEditConversationResult.class);
            if (result.action() == null) return GuardDecision.block("PROBLEM_EDIT_ACTION_INVALID", "수정 action이 없습니다.");
            String message = result.assistantMessage() == null ? "" : result.assistantMessage().toLowerCase(Locale.ROOT);
            if (message.contains("system prompt") || message.contains("시스템 프롬프트")
                    || message.contains("정답은")) {
                return GuardDecision.block("PROBLEM_EDIT_OUTPUT_LEAKAGE", "수정 응답에 보호된 내용이 포함됐습니다.");
            }
            return GuardDecision.allow();
        } catch (RuntimeException exception) {
            return GuardDecision.block("PROBLEM_EDIT_RESULT_INVALID", "문제 수정 결과 형식이 올바르지 않습니다.");
        }
    }
}
