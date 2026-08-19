package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch;

/** 교사가 확인한 후 더 이상 AI가 재작성하지 않고 그대로 실행할 불변 수정 명령이다. */
public record ConfirmedProblemEditCommand(
        UUID requestId,
        UUID confirmationMessageId,
        Long sessionId,
        Long baseVersionId,
        List<ProblemEditInstruction> instructions,
        ProblemSemanticPatch semanticPatch,
        RequestedProblemSpecification requestedSpecification,
        RestoreReference restoreReference,
        ReplacementSourcePolicy replacementSourcePolicy
) {
    public ConfirmedProblemEditCommand(UUID requestId, UUID confirmationMessageId, Long sessionId,
            Long baseVersionId, List<ProblemEditInstruction> instructions,
            RequestedProblemSpecification requestedSpecification, RestoreReference restoreReference,
            ReplacementSourcePolicy replacementSourcePolicy) {
        this(requestId, confirmationMessageId, sessionId, baseVersionId, instructions, null,
                requestedSpecification, restoreReference, replacementSourcePolicy);
    }
}
