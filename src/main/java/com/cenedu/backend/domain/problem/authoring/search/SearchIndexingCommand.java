package com.cenedu.backend.domain.problem.authoring.search;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/** 승인·최종화된 문제를 비동기 검색 인덱싱으로 넘기는 명령이다. */
public record SearchIndexingCommand(
        UUID idempotencyKey, Long questionId, Long authoringVersionId, CurriculumScope curriculum,
        String sourceRef, QuestionSnapshotV1 snapshot, Set<String> conceptKeys,
        Map<String, String> assetStorageKeys) {
    public SearchIndexingCommand(UUID idempotencyKey, Long questionId, Long authoringVersionId,
            CurriculumScope curriculum, String sourceRef, QuestionSnapshotV1 snapshot,
            Set<String> conceptKeys) {
        this(idempotencyKey, questionId, authoringVersionId, curriculum, sourceRef, snapshot,
                conceptKeys, Map.of());
    }
    public SearchIndexingCommand {
        if (idempotencyKey == null || questionId == null || curriculum == null || snapshot == null) {
            throw new IllegalArgumentException("검색 인덱싱 명령의 필수 값이 없습니다.");
        }
        conceptKeys = conceptKeys == null ? Set.of() : Set.copyOf(conceptKeys);
        assetStorageKeys = assetStorageKeys == null ? Map.of() : Map.copyOf(assetStorageKeys);
    }
}
