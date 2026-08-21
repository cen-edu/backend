package com.cenedu.backend.domain.problem.authoring.retrieval;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.Set;
import java.util.UUID;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;

/** 문제 생성에 사용할 참고 문제 검색의 불변 입력 계약이다. */
public record ProblemReferenceQuery(
        UUID retrievalRequestId, GenerationPurpose purpose, CurriculumScope curriculum,
        QuestionType questionType, String difficulty, Long originQuestionId,
        QuestionSnapshotV1 originSnapshot, int candidateLimit, int selectionLimit,
        Set<Long> excludedQuestionIds, VisualReferenceKind requiredVisualKind) {
    public ProblemReferenceQuery(UUID retrievalRequestId, GenerationPurpose purpose, CurriculumScope curriculum,
            QuestionType questionType, String difficulty, Long originQuestionId, QuestionSnapshotV1 originSnapshot,
            int candidateLimit, int selectionLimit, Set<Long> excludedQuestionIds) {
        this(retrievalRequestId, purpose, curriculum, questionType, difficulty, originQuestionId, originSnapshot,
                candidateLimit, selectionLimit, excludedQuestionIds, visualKind(originSnapshot));
    }
    public static ProblemReferenceQuery withVisualKind(UUID retrievalRequestId, GenerationPurpose purpose,
            CurriculumScope curriculum, QuestionType questionType, String difficulty, Long originQuestionId,
            QuestionSnapshotV1 originSnapshot, int candidateLimit, int selectionLimit,
            Set<Long> excludedQuestionIds, VisualReferenceKind requiredVisualKind) {
        return new ProblemReferenceQuery(retrievalRequestId, purpose, curriculum, questionType, difficulty,
                originQuestionId, originSnapshot, candidateLimit, selectionLimit, excludedQuestionIds,
                requiredVisualKind);
    }
    public ProblemReferenceQuery {
        if (retrievalRequestId == null || purpose == null || curriculum == null || questionType == null
                || difficulty == null || difficulty.isBlank()) {
            throw new IllegalArgumentException("검색 조건이 올바르지 않습니다.");
        }
        if (candidateLimit < 1 || candidateLimit > 40) {
            throw new IllegalArgumentException("후보 검색 수는 1 이상 40 이하여야 합니다.");
        }
        if (selectionLimit < 1 || selectionLimit > 4 || selectionLimit > candidateLimit) {
            throw new IllegalArgumentException("선택 검색 수는 1 이상 4 이하여야 합니다.");
        }
        boolean personalized = purpose == GenerationPurpose.PERSONALIZED_SIMILAR_SHORTAGE
                || purpose == GenerationPurpose.PERSONALIZED_APPLICATION;
        if (personalized && (originQuestionId == null || originSnapshot == null)) {
            throw new IllegalArgumentException("맞춤 유사·응용 검색에는 ORIGIN ID와 Snapshot이 필요합니다.");
        }
        if (!personalized && (originQuestionId != null || originSnapshot != null)) {
            throw new IllegalArgumentException("일반·종합평가 검색에는 ORIGIN을 지정할 수 없습니다.");
        }
        excludedQuestionIds = excludedQuestionIds == null ? Set.of() : Set.copyOf(excludedQuestionIds);
        if (requiredVisualKind == null) requiredVisualKind = VisualReferenceKind.NONE;
    }
    private static VisualReferenceKind visualKind(QuestionSnapshotV1 snapshot) {
        if (snapshot == null) return VisualReferenceKind.NONE;
        return switch (snapshot.metadata().presentation().name()) {
            case "WITH_TABLE" -> VisualReferenceKind.DATA_TABLE;
            case "WITH_FIGURE" -> VisualReferenceKind.UNKNOWN_FIGURE;
            default -> VisualReferenceKind.NONE;
        };
    }
}
