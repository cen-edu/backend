package com.cenedu.backend.ai.problem.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.cenedu.backend.ai.problem.adapter.semantic.*;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;

class SpringAiProblemGenerationAdapterTest {
    @Test
    void enabledFlagRoutesToSemanticPipeline() {
        var semantic = mock(ProblemSemanticGenerationPipeline.class);
        var legacy = mock(LegacyProblemGenerationPipeline.class);
        var command = new ProblemGenerationCommand(UUID.randomUUID(), null, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "대", "중", "소"), List.of(), List.of());
        var expected = mock(ProblemCandidateDraft.class);
        when(semantic.generate(command)).thenReturn(expected);
        var adapter = new SpringAiProblemGenerationAdapter(new SemanticAuthoringProperties(true), semantic, legacy);
        assertEquals(expected, adapter.generate(command));
        verify(semantic).generate(command);
        verifyNoInteractions(legacy);
    }
    @Test
    void usesCommonLlmClientAndMapsResponseWithoutCallingOpenAiDirectly() {
        LlmClient client = mock(LlmClient.class);
        UUID requestId = UUID.randomUUID();
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("""
                {"question":"12를 구하시오.","explanation":"계산한다.",
                 "learningGuide":{"conceptTitle":"연산","summary":"연산 개념","keyPoints":["연산 규칙"]},
                 "answerUnits":[{"answerRaw":"12","compareMethod":"VALUE"}]}
                """, 1, 1, 0));
        ObjectProvider<com.fasterxml.jackson.databind.ObjectMapper> mapper = mock(ObjectProvider.class);
        when(mapper.getIfAvailable(any())).thenReturn(new com.fasterxml.jackson.databind.ObjectMapper());
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        var adapter = new SpringAiProblemGenerationAdapter(client, mapper,
                new ProblemGenerationPromptFactory(), new ProblemGenerationOutputMapper(), structural,
                new SnapshotNormalizedValidator(structural));
        var command = new ProblemGenerationCommand(requestId, null, GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "대", "중", "소"), List.of(), List.of());

        var draft = adapter.generate(command);

        assertEquals(requestId, draft.requestId());
        verify(client).completeStructured(any(), any(), any());
    }
}
