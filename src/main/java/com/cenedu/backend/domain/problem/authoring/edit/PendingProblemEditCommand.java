package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;
import java.util.UUID;

/** 수집을 끝내고 교사의 최종 확인을 기다리는 수정 명령이다. */
public record PendingProblemEditCommand(
        UUID requestId,
        Long sessionId,
        Long baseVersionId,
        List<ProblemEditInstruction> instructions,
        RequestedProblemSpecification requestedSpecification,
        RestoreReference restoreReference,
        ReplacementSourcePolicy replacementSourcePolicy
) {
}
