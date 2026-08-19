package com.cenedu.backend.domain.problem.authoring.semantic.extraction;

import java.util.List;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

public record SemanticExtractionResult(SemanticExtractionStatus status,
        ProblemSemanticModelV1 semanticModel, List<String> findings) {
    public SemanticExtractionResult {
        findings = findings == null ? List.of() : List.copyOf(findings);
    }
}
