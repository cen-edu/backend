package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

/** 확정된 수정 실행 계획과 그 기준 Snapshot을 함께 고정한다. */
public record ProblemModificationCommand(
        UUID requestId,
        ProblemEditExecutionPlan plan,
        QuestionSnapshotV1 baseSnapshot,
        ProblemSemanticModelV1 baseSemanticModel
) {
    public ProblemModificationCommand(UUID requestId, ProblemEditExecutionPlan plan,
            QuestionSnapshotV1 baseSnapshot) {
        this(requestId, plan, baseSnapshot, null);
    }
}
