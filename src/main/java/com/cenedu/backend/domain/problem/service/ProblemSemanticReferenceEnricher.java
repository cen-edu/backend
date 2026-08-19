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
        return enrichWithStatus(command).command();
    }

    /** ORIGIN 실패를 fallback 조정기가 식별할 수 있도록 상태를 함께 반환한다. */
    public SemanticReferenceEnrichmentResult enrichWithStatus(ProblemGenerationCommand command) {
        var references = new ArrayList<GenerationReference>();
        int extractedExamples = 0;
        boolean extractExamples = command.specification().requiresSolutionStructure();
        boolean unsupportedOrigin = false;
        for (GenerationReference reference : command.references()) {
            if (reference.role() == GenerationReferenceRole.ORIGIN
                    && reference.semanticModel() == null && reference.sourceQuestionId() != null) {
                var result = extractionService.ensureQuestionSemantic(reference.sourceQuestionId(),
                        command.curriculum(), reference.snapshot());
                unsupportedOrigin = result.status() != com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED;
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), result.status() == com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED
                                ? result.semanticModel() : null));
            } else if (extractExamples && reference.role() == GenerationReferenceRole.EXAMPLE
                    && reference.semanticModel() == null && reference.sourceQuestionId() != null
                    && extractedExamples < 2) {
                var result = extractionService.ensureQuestionSemantic(reference.sourceQuestionId(),
                        command.curriculum(), reference.snapshot());
                extractedExamples++;
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), result.status() == com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED
                                ? result.semanticModel() : null));
            } else {
                references.add(reference);
            }
        }
        ProblemGenerationCommand enriched = new ProblemGenerationCommand(command.requestId(), command.retrievalRequestId(), command.purpose(),
                command.specification(), command.curriculum(), references, command.conceptEvidence());
        return new SemanticReferenceEnrichmentResult(enriched, unsupportedOrigin);
    }
}
