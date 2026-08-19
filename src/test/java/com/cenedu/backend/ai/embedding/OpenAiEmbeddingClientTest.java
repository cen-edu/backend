package com.cenedu.backend.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.Embedding;
import com.openai.models.embeddings.EmbeddingCreateParams;
import com.openai.services.blocking.EmbeddingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenAiEmbeddingClientTest {
    private final OpenAIClient client = mock(OpenAIClient.class);
    private final EmbeddingService service = mock(EmbeddingService.class);
    private final EmbeddingProperties properties = new EmbeddingProperties("text-embedding-3-small", 1024);
    private final OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(client, properties);

    @Test
    void sendsFixedModelInputAndDimensions() {
        when(client.embeddings()).thenReturn(service);
        Embedding embedding = mock(Embedding.class);
        when(embedding.embedding()).thenReturn(vector());
        CreateEmbeddingResponse response = mock(CreateEmbeddingResponse.class);
        when(response.data()).thenReturn(List.of(embedding));
        when(response.model()).thenReturn("text-embedding-3-small");
        when(service.create(any(EmbeddingCreateParams.class))).thenReturn(response);

        EmbeddingResult result = embeddingClient.embed("[발문] 정수의 합");

        ArgumentCaptor<EmbeddingCreateParams> captor = ArgumentCaptor.forClass(EmbeddingCreateParams.class);
        verify(service).create(captor.capture());
        EmbeddingCreateParams params = captor.getValue();
        assertThat(params.input().toString()).contains("정수의 합");
        assertThat(params.model().toString()).contains("text-embedding-3-small");
        assertThat(params.dimensions()).contains(1024L);
        assertThat(result.vector()).hasSize(1024);
    }

    @Test
    void rejectsBlankInputAndWrongResponseShape() {
        assertThatThrownBy(() -> embeddingClient.embed("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("임베딩 입력 문서는 필수입니다.");

        when(client.embeddings()).thenReturn(service);
        CreateEmbeddingResponse response = mock(CreateEmbeddingResponse.class);
        when(response.data()).thenReturn(List.of());
        when(service.create(any(EmbeddingCreateParams.class))).thenReturn(response);
        assertThatThrownBy(() -> embeddingClient.embed("문서"))
                .isInstanceOf(EmbeddingCallException.class)
                .hasFieldOrPropertyWithValue("retryable", false);
    }

    @Test
    void wrapsProviderFailureAsRetryable() {
        when(client.embeddings()).thenReturn(service);
        when(service.create(any(EmbeddingCreateParams.class))).thenThrow(new OpenAIException("429 rate limit"));
        assertThatThrownBy(() -> embeddingClient.embed("문서"))
                .isInstanceOf(EmbeddingCallException.class)
                .hasFieldOrPropertyWithValue("retryable", true)
                .hasMessage("임베딩 Provider 호출에 실패했습니다.");
    }

    private static List<Float> vector() {
        return java.util.stream.IntStream.range(0, 1024).mapToObj(i -> (float) i).toList();
    }
}
