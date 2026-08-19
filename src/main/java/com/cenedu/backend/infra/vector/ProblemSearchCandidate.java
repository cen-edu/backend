package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;

public record ProblemSearchCandidate(
        long questionId, int denseRank, double denseScore, List<Float> vector,
        String duplicateClusterKey, String sourceFamilyKey, QuestionType questionType,
        String difficulty, QuestionSnapshotV1 snapshot, String documentHash) {
    public ProblemSearchCandidate {
        vector = vector == null ? List.of() : List.copyOf(vector);
    }
}
