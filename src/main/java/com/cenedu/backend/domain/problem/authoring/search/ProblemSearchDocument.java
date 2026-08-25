package com.cenedu.backend.domain.problem.authoring.search;

import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;

/** 검색 인덱스에 저장할 정규 문서와 품질 추적용 키다. */
public record ProblemSearchDocument(
        String documentText, String documentHash, String duplicateClusterKey,
        String sourceFamilyKey, String solutionStrategy, String solutionSummary, VisualReferenceKind visualKind) {
    public ProblemSearchDocument(String documentText, String documentHash, String duplicateClusterKey,
            String sourceFamilyKey, String solutionStrategy, String solutionSummary) {
        this(documentText, documentHash, duplicateClusterKey, sourceFamilyKey, solutionStrategy, solutionSummary, VisualReferenceKind.NONE);
    }
}
