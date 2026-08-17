package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;

/** 원본과 확정 요청·허용 범위를 수정 후보 검증에 제공한다. */
public record EditVerificationContext(
        QuestionSnapshotV1 baseSnapshot,
        List<ProblemEditInstruction> confirmedInstructions,
        List<ProblemEditTargetRef> requestedTargets,
        List<ProblemEditTargetRef> dependentTargets,
        List<ProblemEditTargetRef> protectedTargets
) implements VerificationContext {
}
