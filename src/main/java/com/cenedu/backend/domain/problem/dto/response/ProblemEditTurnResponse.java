package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.edit.*;

/** 문제 수정 Agent 한 턴의 화면 응답이다. */
public record ProblemEditTurnResponse(
        EditConversationAction action,
        List<ProblemEditInstruction> instructionDeltas,
        String assistantMessage
) {
    public static ProblemEditTurnResponse from(ProblemEditConversationResult result) {
        return new ProblemEditTurnResponse(result.action(),
                result.instructionDeltas() == null ? List.of() : List.copyOf(result.instructionDeltas()),
                result.assistantMessage());
    }
}
