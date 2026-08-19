package com.cenedu.backend.domain.problem.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.problem.rag")
public record ProblemRagProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("A_DENSE_MMR_V1") String policyVersion,
        @DefaultValue("40") int candidateLimit,
        @DefaultValue("3") int standardSelectionLimit,
        @DefaultValue("4") int personalizedSelectionLimit,
        @DefaultValue("0.70") double defaultLambda,
        @DefaultValue("0.55") double applicationLambda,
        @DefaultValue("2s") Duration searchTimeout,
        @DefaultValue Indexing indexing) {
    public record Indexing(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("20") int batchSize,
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("30s") Duration retryDelay,
            @DefaultValue("5s") Duration workerDelay,
            @DefaultValue("60s") Duration backfillDelay) {}
}
