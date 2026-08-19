package com.cenedu.backend.ai.embedding;

public interface EmbeddingClient {
    /** 검색 문서를 Provider 임베딩 벡터로 변환한다. */
    EmbeddingResult embed(String text);
}
