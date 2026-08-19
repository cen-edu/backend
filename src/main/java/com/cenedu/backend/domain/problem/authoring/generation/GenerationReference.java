package com.cenedu.backend.domain.problem.authoring.generation;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

/** DB Entity 대신 questionId와 S1 스냅샷으로 생성 참고 문제를 전달한다. */
public record GenerationReference(
        GenerationReferenceRole role,
        Long sourceQuestionId,
        QuestionSnapshotV1 snapshot,
        ProblemSemanticModelV1 semanticModel
) {
    public GenerationReference(GenerationReferenceRole role, Long sourceQuestionId,
            QuestionSnapshotV1 snapshot) {
        this(role, sourceQuestionId, snapshot, null);
    }
}
