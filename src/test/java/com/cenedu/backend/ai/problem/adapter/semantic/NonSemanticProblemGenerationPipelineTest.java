package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.problem.adapter.ProblemGenerationOutputMapper;
import com.cenedu.backend.ai.problem.adapter.ProblemGenerationPromptFactory;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationSpecification;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class NonSemanticProblemGenerationPipelineTest {

    private static final String VALID = """
            {"question":"12를 구하시오.","explanation":"계산한다.",
             "learningGuide":{"conceptTitle":"연산","summary":"연산 개념","keyPoints":["연산 규칙"]},
             "answerUnits":[{"answerRaw":"12","compareMethod":"VALUE"}]}
            """;

    @Test
    void invalidThenValidResponseRetriesWithFeedbackAndSucceeds() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any()))
                .thenReturn(new LlmResponse("{}", 1, 1, 0))   // learningGuide 누락 → 내용 위반
                .thenReturn(new LlmResponse(VALID, 1, 1, 0));

        var draft = pipeline(client).generate(command());

        assertThat(draft.snapshot().answerUnits().getFirst().answerRaw()).isEqualTo("12");
        verify(client, times(2)).completeStructured(any(), any(), any());
    }

    @Test
    void threeInvalidResponsesThrowRetryExhausted() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("{}", 1, 1, 0));

        assertThatThrownBy(() -> pipeline(client).generate(command()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PROBLEM_GENERATION_RETRY_EXHAUSTED));
        verify(client, times(3)).completeStructured(any(), any(), any());
    }

    @Test
    void infrastructureFailureIsRethrownImmediatelyWithoutContentRetry() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.AI_CLIENT_CALL_FAILED, "boom"));

        assertThatThrownBy(() -> pipeline(client).generate(command()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.AI_CLIENT_CALL_FAILED));
        verify(client, times(1)).completeStructured(any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private NonSemanticProblemGenerationPipeline pipeline(LlmClient client) {
        ObjectProvider<ObjectMapper> mapperProvider = mock(ObjectProvider.class);
        when(mapperProvider.getIfAvailable(any())).thenReturn(new ObjectMapper());
        SnapshotStructuralValidator structural = new SnapshotStructuralValidator();
        return new NonSemanticProblemGenerationPipeline(client, mapperProvider,
                new ProblemGenerationPromptFactory(), new ProblemGenerationOutputMapper(),
                structural, new SnapshotNormalizedValidator(structural));
    }

    private ProblemGenerationCommand command() {
        return new ProblemGenerationCommand(UUID.randomUUID(), null,
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE,
                new GenerationSpecification(QuestionType.SHORT_INPUT, "mid", null, List.of()),
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "대", "중", "소"),
                List.of(), List.of());
    }
}
