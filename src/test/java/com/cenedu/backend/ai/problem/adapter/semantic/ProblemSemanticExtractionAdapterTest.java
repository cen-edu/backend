package com.cenedu.backend.ai.problem.adapter.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import java.util.UUID;
import com.cenedu.backend.ai.client.LlmClient;
import com.cenedu.backend.ai.client.LlmResponse;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.*;
import com.cenedu.backend.domain.problem.support.ProblemSnapshotFixtures;
import org.junit.jupiter.api.Test;

class ProblemSemanticExtractionAdapterTest {
    @Test
    void provider_json을_extracted_model로_변환한다() {
        LlmClient client = mock(LlmClient.class);
        ProblemSemanticOutputParser parser = mock(ProblemSemanticOutputParser.class);
        var model = mock(com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("{}", 1, 1, 0));
        when(parser.parse("{}")).thenReturn(model);
        var adapter = new ProblemSemanticExtractionAdapter(client,
                new ProblemSemanticExtractionPromptFactory(), parser);

        SemanticExtractionResult result = adapter.extract(command());

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.EXTRACTED);
        assertThat(result.semanticModel()).isSameAs(model);
    }

    @Test
    void parser_실패는_invalid_source로_분류한다() {
        LlmClient client = mock(LlmClient.class);
        ProblemSemanticOutputParser parser = mock(ProblemSemanticOutputParser.class);
        when(client.completeStructured(any(), any(), any())).thenReturn(new LlmResponse("{}", 1, 1, 0));
        when(parser.parse("{}")).thenThrow(new IllegalArgumentException("bad json"));

        var result = new ProblemSemanticExtractionAdapter(client,
                new ProblemSemanticExtractionPromptFactory(), parser).extract(command());

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.INVALID_SOURCE);
    }

    @Test
    void provider_실패는_technical_error로_분류한다() {
        LlmClient client = mock(LlmClient.class);
        when(client.completeStructured(any(), any(), any())).thenThrow(new IllegalStateException("down"));

        var result = new ProblemSemanticExtractionAdapter(client,
                new ProblemSemanticExtractionPromptFactory(), mock(ProblemSemanticOutputParser.class))
                .extract(command());

        assertThat(result.status()).isEqualTo(SemanticExtractionStatus.TECHNICAL_ERROR);
    }

    private SemanticExtractionCommand command() {
        return new SemanticExtractionCommand(UUID.randomUUID(), 41L,
                new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L,
                        "수와 연산", "사칙연산", "덧셈"), ProblemSnapshotFixtures.shortInput());
    }
}
