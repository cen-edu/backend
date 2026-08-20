package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;

@Component
public final class ProblemSemanticGenerationPipeline {
    private final LlmClient client;
    private final ProblemSemanticGenerationPromptFactory prompts;
    private final ProblemSemanticOutputParser parser;
    private final ProblemSemanticMaterializer materializer;
    private final ObjectMapper mapper;

    public ProblemSemanticGenerationPipeline(LlmClient client, ProblemSemanticGenerationPromptFactory prompts, ProblemSemanticOutputParser parser, ProblemSemanticMaterializer materializer, ObjectProvider<ObjectMapper> mapper) {
        this.client = client;
        this.prompts = prompts;
        this.parser = parser;
        this.materializer = materializer;
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
    }

    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        List<String> findings = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String json = client.completeStructured(prompts.create(command, findings), prompts.messages(command), ProblemStructuredOutputSchemas.SEMANTIC_MODEL).text();
                ProblemSemanticModelV1 parsed = parser.parse(json);
                ProblemSemanticModelV1 serverOwned = new ProblemSemanticModelV1(1, command.curriculum(), parsed.intent(), parsed.parameters(), parsed.computations(), parsed.constraints(), parsed.presentation(), parsed.diagrams(), parsed.assertions());
                MaterializedProblem evaluated = materializer.materialize(serverOwned);
                var normalizedComputations = serverOwned.computations().stream().map(c -> new SemanticComputation(c.key(), c.operation(), c.operands(), c.literal(), c.unit(), evaluated.report().resolvedValues().get(c.key()))).toList();
                ProblemSemanticModelV1 normalized = new ProblemSemanticModelV1(1, command.curriculum(), serverOwned.intent(), serverOwned.parameters(), normalizedComputations, serverOwned.constraints(), serverOwned.presentation(), serverOwned.diagrams(), serverOwned.assertions());
                MaterializedProblem materialized = materializer.materialize(normalized);
                return new ProblemCandidateDraft(command.requestId(), materialized.snapshot(), materialized.assetPlans(), normalized, new CandidateProvenance(CandidateSourceType.AI_GENERATE, null, command.references().stream().map(x -> x.sourceQuestionId()).toList()));
            } catch (RuntimeException e) {
                findings = violationMessages(e);
            }
        }
        throw new SemanticGenerationException(findings);
    }

    private List<String> violationMessages(RuntimeException e) {
        if (e instanceof com.cenedu.backend.domain.problem.authoring.semantic.validation.SemanticValidationException x)
            return x.violations().stream().limit(10).map(this::truncate).toList();
        return List.of(truncate(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
    }

    private String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }

    public static final class SemanticGenerationException extends RuntimeException {
        public SemanticGenerationException(List<String> findings) {
            super("semantic generation retry exhausted: " + findings);
        }
    }
}
