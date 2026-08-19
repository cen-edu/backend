package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.cenedu.backend.ai.client.*;
import com.cenedu.backend.ai.problem.adapter.FewShotReferenceSerializer;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class ProblemSemanticGenerationPipelineTest {
    @Test void invalidFirstResponseIsRepairedAndServerNormalizesResult() throws Exception {
        var client=mock(LlmClient.class); var model=fixture(); var json=new ObjectMapper().writeValueAsString(model);
        when(client.completeStructured(anyString(),anyList(),anyString())).thenReturn(new LlmResponse("{}",0,0,0)).thenReturn(new LlmResponse(json,0,0,0));
        var pipeline=pipeline(client); var candidate=pipeline.generate(command());
        assertThat(candidate.semanticModel().computations().getFirst().result()).isEqualTo("5");
        assertThat(candidate.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("5");
        verify(client,times(2)).completeStructured(anyString(),anyList(),anyString());
    }

    @Test void threeInvalidResponsesExhaustRetryBudget() {
        var client=mock(LlmClient.class); when(client.completeStructured(anyString(),anyList(),anyString())).thenReturn(new LlmResponse("{}",0,0,0));
        assertThatThrownBy(() -> pipeline(client).generate(command())).isInstanceOf(ProblemSemanticGenerationPipeline.SemanticGenerationException.class);
        verify(client,times(3)).completeStructured(anyString(),anyList(),anyString());
    }

    private ProblemSemanticGenerationPipeline pipeline(LlmClient c){var provider=mock(org.springframework.beans.factory.ObjectProvider.class);when(provider.getIfAvailable(any())).thenReturn(new ObjectMapper());return new ProblemSemanticGenerationPipeline(c,new ProblemSemanticGenerationPromptFactory(new FewShotReferenceSerializer()),new ProblemSemanticOutputParser(provider),new DefaultProblemSemanticMaterializer(),provider);}
    private ProblemGenerationCommand command(){return new ProblemGenerationCommand(UUID.randomUUID(),null,GenerationPurpose.GENERAL_LEARNING_SHORTAGE,new GenerationSpecification(QuestionType.SHORT_INPUT,"mid",null,List.of()),curriculum(),List.of(),List.of());}
    private CurriculumScope curriculum(){return new CurriculumScope("2022_REVISED","MIDDLE",1,1,null,1L,"major","middle","sub");}
    private ProblemSemanticModelV1 fixture(){var p1=new SemanticParameter("A",SemanticValueType.INTEGER,"2",null,false,null);var p2=new SemanticParameter("B",SemanticValueType.INTEGER,"3",null,false,null);var c=new SemanticComputation("C",SemanticOperation.ADD,List.of("A","B"),null,null,"999");var i=new SemanticProblemIntent(QuestionType.SHORT_INPUT,"mid",null,"add","C",1,false);var presentation=new SemanticPresentationPlan("${A}+${B}=?",List.of(),List.of(),"${C}",null,List.of());return new ProblemSemanticModelV1(1,curriculum(),i,List.of(p1,p2),List.of(c),List.of(),presentation,List.of(),List.of());}
}
