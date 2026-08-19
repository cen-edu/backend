package com.cenedu.backend.ai.embedding;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 문제 검색 임베딩 Provider 설정이다. 차원은 pgvector 스키마와 맞춰 1024로 고정한다. */
@ConfigurationProperties(prefix = "app.ai.embedding")
public record EmbeddingProperties(String model, int dimensions) {
    public EmbeddingProperties {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("임베딩 모델은 필수입니다.");
        if (dimensions != 1024) throw new IllegalArgumentException("임베딩 차원은 1024여야 합니다.");
    }
}
