package com.cenedu.backend.domain.problem.authoring.candidate;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

/** 생성·수정 결과를 S1 스냅샷, 자산 계획, 출처와 함께 후속 검증 경로에 넘긴다. */
public record ProblemCandidateDraft(
        UUID requestId,
        QuestionSnapshotV1 snapshot,
        List<GeneratedAssetPlan> assetPlans,
        ProblemSemanticModelV1 semanticModel,
        CandidateProvenance provenance
) {
    public ProblemCandidateDraft(UUID requestId, QuestionSnapshotV1 snapshot,
            List<GeneratedAssetPlan> assetPlans, CandidateProvenance provenance) {
        this(requestId, snapshot, assetPlans, null, provenance);
    }

    public static ProblemCandidateDraft legacy(UUID requestId, QuestionSnapshotV1 snapshot,
            List<GeneratedAssetPlan> plans, CandidateProvenance provenance) {
        return new ProblemCandidateDraft(requestId, snapshot, plans, null, provenance);
    }
}
