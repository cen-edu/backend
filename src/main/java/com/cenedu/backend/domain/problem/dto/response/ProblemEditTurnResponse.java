package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;

/** 문제 수정 Agent 한 턴의 화면 응답이다. */
public record ProblemEditTurnResponse(
        EditConversationAction action,
        List<ProblemEditInstruction> instructionDeltas,
        ProblemSemanticPatch semanticPatch, String assistantMessage,
        ProblemModificationPreviewResponse preview
) {
    public ProblemEditTurnResponse(EditConversationAction action, List<ProblemEditInstruction> instructionDeltas,
            String assistantMessage) {
        this(action, instructionDeltas, null, assistantMessage, null);
    }
    public static ProblemEditTurnResponse from(ProblemEditConversationResult result,
            ProblemModificationExecutionResult executionResult) {
        return new ProblemEditTurnResponse(result.action(),
                result.instructionDeltas() == null ? List.of() : List.copyOf(result.instructionDeltas()),
                result.semanticPatch(), result.assistantMessage(),
                executionResult == null ? null : ProblemModificationPreviewResponse.from(executionResult));
    }
    public static ProblemEditTurnResponse from(ProblemEditConversationResult result) { return from(result, null); }
}
