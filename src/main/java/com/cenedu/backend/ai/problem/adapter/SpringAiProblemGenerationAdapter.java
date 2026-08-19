package com.cenedu.backend.ai.problem.adapter;
import com.cenedu.backend.ai.problem.adapter.semantic.*;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
@Component
public final class SpringAiProblemGenerationAdapter implements ProblemGenerationPort {
    private final SemanticAuthoringProperties properties; private final ProblemSemanticGenerationPipeline semanticPipeline; private final LegacyProblemGenerationPipeline legacyPipeline;
    @Autowired
    public SpringAiProblemGenerationAdapter(SemanticAuthoringProperties properties,ProblemSemanticGenerationPipeline semanticPipeline,LegacyProblemGenerationPipeline legacyPipeline){this.properties=properties;this.semanticPipeline=semanticPipeline;this.legacyPipeline=legacyPipeline;}
    /** Legacy test and direct-construction compatibility; production routing uses the typed constructor. */
    public SpringAiProblemGenerationAdapter(com.cenedu.backend.ai.client.LlmClient client,ObjectProvider<ObjectMapper> mapper,ProblemGenerationPromptFactory prompts,ProblemGenerationOutputMapper output,com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator structural,com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator normalized){this(new SemanticAuthoringProperties(false),null,new LegacyProblemGenerationPipeline(client,mapper,prompts,output,structural,normalized));}
    @Override public ProblemCandidateDraft generate(ProblemGenerationCommand command){return properties.enabled()?semanticPipeline.generate(command):legacyPipeline.generate(command);}
}
