package com.cenedu.backend.domain.problem.dto.request;

import java.util.List;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import jakarta.validation.constraints.NotBlank;

/** 교사가 문제 수정 Agent에 보내는 한 턴 요청이다. */
public record ProblemEditTurnRequest(
        @NotBlank String userInput,
        List<ChatMessage> history,
        ProblemEditTargetRef selectedTarget,
        Boolean confirmed
) {
    public ProblemEditTurnRequest(String userInput, List<ChatMessage> history,
            ProblemEditTargetRef selectedTarget) {
        this(userInput, history, selectedTarget, null);
    }
}
