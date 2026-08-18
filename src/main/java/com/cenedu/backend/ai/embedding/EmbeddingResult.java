package com.cenedu.backend.ai.embedding;

import java.util.List;

public record EmbeddingResult(String model, List<Float> vector) {
    public EmbeddingResult {
        if (model == null || vector == null) throw new IllegalArgumentException("임베딩 결과가 올바르지 않습니다.");
        vector = List.copyOf(vector);
    }
}
