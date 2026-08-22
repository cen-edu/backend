package com.cenedu.backend.ai.problem.adapter;
import com.cenedu.backend.ai.problem.adapter.semantic.*;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.ObjectProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
@Component
public final class SpringAiProblemGenerationAdapter implements ProblemGenerationPort {
    private final SemanticAuthoringProperties properties; private final ProblemSemanticGenerationPipeline semanticPipeline; private final NonSemanticProblemGenerationPipeline nonSemanticPipeline;
    @Autowired
    public SpringAiProblemGenerationAdapter(SemanticAuthoringProperties properties,ProblemSemanticGenerationPipeline semanticPipeline,NonSemanticProblemGenerationPipeline nonSemanticPipeline){this.properties=properties;this.semanticPipeline=semanticPipeline;this.nonSemanticPipeline=nonSemanticPipeline;}
    /** Legacy test and direct-construction compatibility; production routing uses the typed constructor. */
    public SpringAiProblemGenerationAdapter(com.cenedu.backend.ai.client.LlmClient client,ObjectProvider<ObjectMapper> mapper,ProblemGenerationPromptFactory prompts,ProblemGenerationOutputMapper output,com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator structural,com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator normalized){this(new SemanticAuthoringProperties(false),null,new NonSemanticProblemGenerationPipeline(client,mapper,prompts,output,structural,normalized));}
    @Override public ProblemCandidateDraft generate(ProblemGenerationCommand command){
        if (!properties.enabled()) return nonSemanticPipeline.generate(command);
        boolean originUnavailable = command.references().stream().anyMatch(reference ->
                reference.role() == com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole.ORIGIN
                        && reference.semanticModel() == null);
        if (command.specification().visualRequirement().mode() == VisualGenerationMode.PRESERVE_ORIGIN
                && originUnavailable) {
            throw new BusinessException(ErrorCode.PROBLEM_VISUAL_SOURCE_UNSUPPORTED);
        }
        if (command.specification().visualRequirement().mode() == VisualGenerationMode.NONE) {
            return nonSemanticPipeline.generate(command);
        }
        return semanticPipeline.generate(command);
    }
}
