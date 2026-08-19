package com.cenedu.backend.domain.problem.authoring.retrieval;

public enum RetrievalFallbackReason {
    PORT_UNAVAILABLE, PROVIDER_FAILURE, SEARCH_TIMEOUT, NO_CANDIDATES, SNAPSHOT_RESTORE_FAILED
}
