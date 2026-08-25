package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.cenedu.backend.ai.agent.ChatMessage;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.ai.problem.ProblemStructuredOutputSchemas;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionCommand;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticEvaluationException;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.semantic.validation.SemanticValidationException;
import com.fasterxml.jackson.databind.ObjectMapper;

/** semantic extraction의 provider schema와 최대 1회 교정 계약을 검증한다. */
class ProblemSemanticExtractionAdapterTest {

    @Test
    void provider_schema가_parameter_key의_domain_규칙을_강제한다() throws Exception {
        var schema = new ObjectMapper().readTree(ProblemStructuredOutputSchemas.SEMANTIC_MODEL);

        assertThat(schema.at("/$defs/parameter/properties/key/pattern").asText())
                .isEqualTo("^[A-Z][A-Z0-9_]{0,63}$");
    }

    @Test
    void 첫_domain_validation_실패는_한_번_교정해_성공_결과를_반환한다() {
        Fixture fixture = fixture();
        doThrow(new SemanticValidationException(List.of("parameters[0]: 논리 키 형식이 잘못되었습니다.")))
                .doReturn(mock(MaterializedProblem.class))
                .when(fixture.materializer()).materialize(any());

        var result = fixture.adapter().extract(mock(SemanticExtractionCommand.class));

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.EXTRACTED);
        assertThat(result.semanticModel()).isSameAs(fixture.corrected());
        verify(fixture.client(), times(2)).completeStructured(anyString(), anyList(), anyString());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ChatMessage>> messages = ArgumentCaptor.forClass(List.class);
        verify(fixture.client(), times(2)).completeStructured(anyString(), messages.capture(), anyString());
        assertThat(messages.getAllValues().get(1).getLast().content())
                .contains("domain validation").contains("parameters[0]");
    }

    @Test
    void 교정도_validation에_실패하면_추가_호출_없이_INVALID_SOURCE로_종료한다() {
        Fixture fixture = fixture();
        doThrow(new SemanticValidationException(List.of("parameters[0]: key 오류")))
                .doThrow(new SemanticValidationException(List.of("parameters[1]: key 오류")))
                .when(fixture.materializer()).materialize(any());

        var result = fixture.adapter().extract(mock(SemanticExtractionCommand.class));

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.INVALID_SOURCE);
        assertThat(result.findings()).singleElement().asString().contains("repair-materialize");
        verify(fixture.client(), times(2)).completeStructured(anyString(), anyList(), anyString());
    }

    @Test
    void 계산_그래프_계약_오류도_한_번_교정한다() {
        Fixture fixture = fixture();
        doThrow(new SemanticEvaluationException("cycle: A, B"))
                .doReturn(mock(MaterializedProblem.class))
                .when(fixture.materializer()).materialize(any());

        var result = fixture.adapter().extract(mock(SemanticExtractionCommand.class));

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.EXTRACTED);
        assertThat(result.semanticModel()).isSameAs(fixture.corrected());
        verify(fixture.client(), times(2)).completeStructured(anyString(), anyList(), anyString());
    }

    private Fixture fixture() {
        LlmClient client = mock(LlmClient.class);
        ProblemSemanticExtractionPromptFactory prompts = mock(ProblemSemanticExtractionPromptFactory.class);
        ProblemSemanticOutputParser parser = mock(ProblemSemanticOutputParser.class);
        ProblemSemanticMaterializer materializer = mock(ProblemSemanticMaterializer.class);
        ProblemSemanticModelV1 first = mock(ProblemSemanticModelV1.class);
        ProblemSemanticModelV1 corrected = mock(ProblemSemanticModelV1.class);
        when(prompts.systemPrompt()).thenReturn("system");
        when(prompts.messages(any())).thenReturn(List.of(ChatMessage.user("source")));
        when(prompts.correctionMessages(any(), anyString())).thenAnswer(invocation -> List.of(
                ChatMessage.user("source"), ChatMessage.user("domain validation " + invocation.getArgument(1))));
        when(client.completeStructured(anyString(), anyList(), anyString()))
                .thenReturn(new LlmResponse("first", 0, 0, 0), new LlmResponse("corrected", 0, 0, 0));
        when(parser.parse("first")).thenReturn(first);
        when(parser.parse("corrected")).thenReturn(corrected);
        return new Fixture(new ProblemSemanticExtractionAdapter(client, prompts, parser, materializer),
                client, materializer, corrected);
    }

    private record Fixture(ProblemSemanticExtractionAdapter adapter, LlmClient client,
                           ProblemSemanticMaterializer materializer, ProblemSemanticModelV1 corrected) { }
}
