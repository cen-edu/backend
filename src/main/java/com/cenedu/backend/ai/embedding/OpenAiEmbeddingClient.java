package com.cenedu.backend.ai.embedding;

import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.models.embeddings.CreateEmbeddingResponse;
import com.openai.models.embeddings.EmbeddingCreateParams;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OpenAiEmbeddingClient implements EmbeddingClient {
    private final OpenAIClient client;
    private final EmbeddingProperties properties;

    public OpenAiEmbeddingClient(OpenAIClient client, EmbeddingProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public EmbeddingResult embed(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("임베딩 입력 문서는 필수입니다.");
        EmbeddingCreateParams params = EmbeddingCreateParams.builder()
                .input(text).model(properties.model()).dimensions((long) properties.dimensions()).build();
        try {
            CreateEmbeddingResponse response = client.embeddings().create(params);
            if (response.data().size() != 1) {
                throw new EmbeddingCallException("임베딩 응답 개수가 1이 아닙니다.", false);
            }
            List<Float> vector = List.copyOf(response.data().getFirst().embedding());
            if (vector.size() != properties.dimensions()) {
                throw new EmbeddingCallException("임베딩 차원이 1024가 아닙니다.", false);
            }
            return new EmbeddingResult(response.model(), vector);
        } catch (OpenAIException exception) {
            throw new EmbeddingCallException("임베딩 Provider 호출에 실패했습니다.", true, exception);
        }
    }
}
