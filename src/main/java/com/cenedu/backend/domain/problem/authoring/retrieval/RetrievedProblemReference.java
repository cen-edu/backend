package com.cenedu.backend.domain.problem.authoring.retrieval;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import java.util.Set;

/** 검색에서 선택된 참고 문제와 재현 가능한 검색 점수 정보다. */
public record RetrievedProblemReference(
        Long questionId, QuestionSnapshotV1 snapshot, double denseScore, int denseRank,
        String documentHash, String duplicateClusterKey, Set<String> matchedConceptKeys) {
    public RetrievedProblemReference {
        if (questionId == null || snapshot == null || documentHash == null || duplicateClusterKey == null) {
            throw new IllegalArgumentException("검색 참고 문제의 필수 값이 없습니다.");
        }
        matchedConceptKeys = matchedConceptKeys == null ? Set.of() : Set.copyOf(matchedConceptKeys);
    }
}
