package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.ai.problem.adapter.semantic.SemanticAuthoringProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** semantic generation 직전에 ORIGIN 참고 문항의 lazy extraction을 수행한다. */
@Component
public class ProblemSemanticReferenceEnricher {
    private final ProblemSemanticExtractionService extractionService;
    private final SemanticAuthoringProperties properties;

    public ProblemSemanticReferenceEnricher(ProblemSemanticExtractionService extractionService) {
        this(extractionService, new SemanticAuthoringProperties(true));
    }

    @Autowired
    public ProblemSemanticReferenceEnricher(ProblemSemanticExtractionService extractionService,
                                            SemanticAuthoringProperties properties) {
        this.extractionService = extractionService;
        this.properties = properties;
    }

    /** ORIGIN만 즉시 보강하고 EXAMPLE은 snapshot-only로 유지한다. */
    public ProblemGenerationCommand enrich(ProblemGenerationCommand command) {
        return enrichWithStatus(command).command();
    }

    /** ORIGIN 실패를 fallback 조정기가 식별할 수 있도록 상태를 함께 반환한다. */
    public SemanticReferenceEnrichmentResult enrichWithStatus(ProblemGenerationCommand command) {
        if (!properties.enabled()) {
            return new SemanticReferenceEnrichmentResult(command, false);
        }
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
                var semanticModel = result.status() == com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED
                        ? result.semanticModel() : null;
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), semanticModel,
                        enrichVisual(reference, semanticModel)));
            } else if (extractExamples && reference.role() == GenerationReferenceRole.EXAMPLE
                    && reference.semanticModel() == null && reference.sourceQuestionId() != null
                    && extractedExamples < 2) {
                var result = extractionService.ensureQuestionSemantic(reference.sourceQuestionId(),
                        command.curriculum(), reference.snapshot());
                extractedExamples++;
                var semanticModel = result.status() == com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus.EXTRACTED
                        ? result.semanticModel() : null;
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), semanticModel,
                        enrichVisual(reference, semanticModel)));
            } else {
                references.add(new GenerationReference(reference.role(), reference.sourceQuestionId(),
                        reference.snapshot(), reference.semanticModel(),
                        enrichVisual(reference, reference.semanticModel())));
            }
        }
        ProblemGenerationCommand enriched = new ProblemGenerationCommand(command.requestId(), command.retrievalRequestId(), command.purpose(),
                command.specification(), command.curriculum(), references, command.conceptEvidence(),
                command.personalizedEvidence(), command.editInstruction());
        if (unsupportedOrigin && coordinateGraphPreservation(command)) {
            enriched = coordinateGraphFallback(enriched);
            unsupportedOrigin = false;
        }
        return new SemanticReferenceEnrichmentResult(enriched, unsupportedOrigin);
    }

    private com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceDescriptor enrichVisual(
            GenerationReference reference,
            com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1 semanticModel) {
        var current = reference.visualReference();
        if (semanticModel == null || semanticModel.diagrams().size() != 1) return current;
        var diagram = semanticModel.diagrams().getFirst();
        var asset = reference.snapshot().assets() == null || reference.snapshot().assets().isEmpty()
                ? null : reference.snapshot().assets().getFirst();
        return new com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceDescriptor(
                current != null ? current.assetKey() : asset == null ? null : asset.assetKey(),
                com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind.fromDiagramKind(
                        diagram.kind()),
                current == null ? null : current.role(),
                current != null ? current.altText() : asset == null ? "" : asset.altText(),
                diagram);
    }

    private boolean coordinateGraphPreservation(ProblemGenerationCommand command) {
        var visual = command.specification().visualRequirement();
        return visual.mode() == com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode.PRESERVE_ORIGIN
                && visual.requiredKind()
                == com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind.COORDINATE_GRAPH;
    }

    private ProblemGenerationCommand coordinateGraphFallback(ProblemGenerationCommand command) {
        var specification = command.specification();
        var fallbackSpecification = new GenerationSpecification(specification.questionType(),
                specification.difficulty(), specification.targetEvaluationArea(),
                specification.targetDiagnosticTypes(), specification.requiresSolutionStructure(),
                new com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement(
                        com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode.REQUIRED,
                        com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind.COORDINATE_GRAPH));
        return new ProblemGenerationCommand(command.requestId(), command.retrievalRequestId(),
                command.purpose(), fallbackSpecification, command.curriculum(), command.references(),
                command.conceptEvidence(), command.personalizedEvidence(), command.editInstruction());
    }
}
