package com.cenedu.backend.domain.problem.dto.response;

import java.util.List;
import java.util.Set;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;

/** 확정 실행 결과를 answer-free 형태로 요약한 preview다. */
public record ProblemModificationPreviewResponse(Long previewVersionId, SemanticEditMode mode,
        List<ProblemParameterChangeResponse> parameterChanges, Set<SemanticImpactArea> impactedAreas,
        boolean structuralChange, boolean revalidationRequired, boolean legacyFallback) {
    public static ProblemModificationPreviewResponse from(ProblemModificationExecutionResult result) {
        return new ProblemModificationPreviewResponse(result.previewVersionId(), result.mode(),
                result.diff().parameterChanges().stream().map(ProblemParameterChangeResponse::from).toList(),
                Set.copyOf(result.diff().impactedAreas()), result.diff().structuralChange(),
                result.diff().revalidationRequired(), result.legacyFallback());
    }
}
