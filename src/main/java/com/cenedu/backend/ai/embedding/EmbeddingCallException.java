package com.cenedu.backend.ai.embedding;

public class EmbeddingCallException extends RuntimeException {
    private final boolean retryable;

    public EmbeddingCallException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public EmbeddingCallException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean retryable() { return retryable; }
}
