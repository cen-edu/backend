package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;

/** ProblemEditAgent의 한 턴 해석 결과로, 새로 추가된 지시만 도메인에 반환한다. */
public record ProblemEditConversationResult(
        EditConversationAction action,
        List<ProblemEditInstruction> instructionDeltas,
        String assistantMessage
) {
}
