package com.cenedu.backend.domain.problem.authoring.edit;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

/** 확정된 수정 실행 계획과 그 기준 Snapshot을 함께 고정한다. */
public record ProblemModificationCommand(
        UUID requestId,
        ProblemEditExecutionPlan plan,
        QuestionSnapshotV1 baseSnapshot,
        ProblemSemanticModelV1 baseSemanticModel,
        List<GeneratedAssetPlan> baseAssetPlans,
        CurriculumScope curriculum,
        List<GenerationReference> references,
        List<VerificationIssueCode> previousIssueCodes
) {
    public ProblemModificationCommand {
        baseAssetPlans = baseAssetPlans == null ? List.of() : List.copyOf(baseAssetPlans);
        references = references == null ? List.of() : List.copyOf(references);
        previousIssueCodes = previousIssueCodes == null ? List.of() : List.copyOf(previousIssueCodes);
    }

    public ProblemModificationCommand(UUID requestId, ProblemEditExecutionPlan plan,
            QuestionSnapshotV1 baseSnapshot, ProblemSemanticModelV1 baseSemanticModel,
            List<GeneratedAssetPlan> baseAssetPlans, List<VerificationIssueCode> previousIssueCodes) {
        this(requestId, plan, baseSnapshot, baseSemanticModel, baseAssetPlans, null, List.of(),
                previousIssueCodes);
    }

    public ProblemModificationCommand(UUID requestId, ProblemEditExecutionPlan plan,
            QuestionSnapshotV1 baseSnapshot, ProblemSemanticModelV1 baseSemanticModel) {
        this(requestId, plan, baseSnapshot, baseSemanticModel, List.of(), null, List.of(), List.of());
    }

    public ProblemModificationCommand(UUID requestId, ProblemEditExecutionPlan plan,
            QuestionSnapshotV1 baseSnapshot) {
        this(requestId, plan, baseSnapshot, null, List.of(), null, List.of(), List.of());
    }
}
