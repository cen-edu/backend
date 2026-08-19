package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch;

/** ProblemEditPolicy가 확정 명령에서 계산한 실행 방법과 수정 허용 범위다. */
public record ProblemEditExecutionPlan(
        UUID requestId,
        Long sessionId,
        Long baseVersionId,
        EditAction action,
        ReplacementSourcePolicy sourcePolicy,
        Long restoreVersionId,
        List<ProblemEditInstruction> instructions,
        ProblemSemanticPatch semanticPatch,
        List<ProblemEditTargetRef> requestedTargets,
        List<ProblemEditTargetRef> dependentTargets,
        List<ProblemEditTargetRef> protectedTargets,
        RequestedProblemSpecification requestedSpecification
) {
    public ProblemEditExecutionPlan(UUID requestId, Long sessionId, Long baseVersionId,
            EditAction action, ReplacementSourcePolicy sourcePolicy, Long restoreVersionId,
            List<ProblemEditInstruction> instructions, List<ProblemEditTargetRef> requestedTargets,
            List<ProblemEditTargetRef> dependentTargets, List<ProblemEditTargetRef> protectedTargets,
            RequestedProblemSpecification requestedSpecification) {
        this(requestId, sessionId, baseVersionId, action, sourcePolicy, restoreVersionId, instructions,
                null, requestedTargets, dependentTargets, protectedTargets, requestedSpecification);
    }
}
