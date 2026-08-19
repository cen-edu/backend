package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import org.springframework.stereotype.Component;

/** semantic generation 직전에 ORIGIN 참고 문항의 lazy extraction을 수행한다. */
@Component
public class ProblemSemanticReferenceEnricher {
    private final ProblemSemanticExtractionService extractionService;

    public ProblemSemanticReferenceEnricher(ProblemSemanticExtractionService extractionService) {
        this.extractionService = extractionService;
    }

    /** ORIGIN만 즉시 보강하고 EXAMPLE은 snapshot-only로 유지한다. */
    public ProblemGenerationCommand enrich(ProblemGenerationCommand command) {
        var references = new ArrayList<GenerationReference>();
        for (GenerationReference reference : command.references()) {
            if (reference.role() == GenerationReferenceRole.ORIGIN
                    && reference.semanticModel() == null && reference.sourceQuestionId() != null) {
                var result = extractionService.ensureQuestionSemantic(reference.sourceQuestionId(),
                        command.curriculum(), reference.snapshot());
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), result.status() == com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED
                                ? result.semanticModel() : null));
            } else {
                references.add(reference);
            }
        }
        return new ProblemGenerationCommand(command.requestId(), command.retrievalRequestId(), command.purpose(),
                command.specification(), command.curriculum(), references, command.conceptEvidence());
    }
}
