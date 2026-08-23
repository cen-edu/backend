package com.cenedu.backend.ai.problem.adapter.semantic;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationPolicy;
import com.cenedu.backend.domain.problem.authoring.visual.VisualSnapshotConsistencyValidator;

@Component
public final class ProblemSemanticGenerationPipeline {
    private final LlmClient client;
    private final ProblemSemanticGenerationPromptFactory prompts;
    private final ProblemSemanticOutputParser parser;
    private final ProblemSemanticMaterializer materializer;
    private final ObjectMapper mapper;
    private final VisualGenerationPolicy visualPolicy;
    private final VisualSnapshotConsistencyValidator visualConsistencyValidator = new VisualSnapshotConsistencyValidator();

    public ProblemSemanticGenerationPipeline(LlmClient client, ProblemSemanticGenerationPromptFactory prompts, ProblemSemanticOutputParser parser, ProblemSemanticMaterializer materializer, ObjectProvider<ObjectMapper> mapper) {
        this(client, prompts, parser, materializer, mapper, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ProblemSemanticGenerationPipeline(LlmClient client, ProblemSemanticGenerationPromptFactory prompts, ProblemSemanticOutputParser parser, ProblemSemanticMaterializer materializer, ObjectProvider<ObjectMapper> mapper, ObjectProvider<ProblemVisualAuthoringProperties> visualProperties) {
        this.client = client;
        this.prompts = prompts;
        this.parser = parser;
        this.materializer = materializer;
        this.mapper = mapper.getIfAvailable(ObjectMapper::new);
        this.visualPolicy = new VisualGenerationPolicy(visualProperties == null
                ? new ProblemVisualAuthoringProperties(false, Set.of(), Set.of(), 1)
                : visualProperties.getIfAvailable(() -> new ProblemVisualAuthoringProperties(false, Set.of(), Set.of(), 1)));
    }

    public ProblemCandidateDraft generate(ProblemGenerationCommand command) {
        List<String> findings = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                String json = client.completeStructured(prompts.create(command, findings), prompts.messages(command), ProblemStructuredOutputSchemas.SEMANTIC_MODEL).text();
                ProblemSemanticModelV1 parsed = parser.parse(json);
                ProblemSemanticModelV1 serverOwned = new ProblemSemanticModelV1(1, command.curriculum(), parsed.intent(), parsed.parameters(), parsed.computations(), parsed.constraints(), parsed.presentation(), parsed.diagrams(), parsed.assertions());
                visualPolicy.validate(command.specification().visualRequirement(), serverOwned);
                MaterializedProblem evaluated = materializer.materialize(serverOwned);
                var normalizedComputations = serverOwned.computations().stream().map(c -> new SemanticComputation(c.key(), c.operation(), c.operands(), c.literal(), c.unit(), evaluated.report().resolvedValues().get(c.key()))).toList();
                ProblemSemanticModelV1 normalized = new ProblemSemanticModelV1(1, command.curriculum(), serverOwned.intent(), serverOwned.parameters(), normalizedComputations, serverOwned.constraints(), serverOwned.presentation(), serverOwned.diagrams(), serverOwned.assertions());
                MaterializedProblem materialized = materializer.materialize(normalized);
                visualConsistencyValidator.validate(normalized, materialized.snapshot(), materialized.assetPlans());
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

    /**
     * 내부 3회 재시도(오류 findings를 프롬프트에 실어 재호출)를 모두 소진했다는 뜻이다.
     * {@link ErrorCode#PROBLEM_GENERATION_RETRY_EXHAUSTED}로 던져, 호출부(worker)가 같은 실패를
     * 근거 없이 다시 반복하지 않고 여기서 멈추게 한다.
     */
    public static final class SemanticGenerationException extends BusinessException {
        public SemanticGenerationException(List<String> findings) {
            super(ErrorCode.PROBLEM_GENERATION_RETRY_EXHAUSTED,
                    "semantic generation retry exhausted: " + findings);
        }
    }
}
