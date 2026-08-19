package com.cenedu.backend.infra.vector;

import com.cenedu.backend.ai.embedding.EmbeddingClient;
import com.cenedu.backend.domain.problem.authoring.retrieval.*;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocumentFactory;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class PgVectorProblemReferenceRetrievalAdapter implements ProblemReferenceRetrievalPort {
    private static final Logger log = LoggerFactory.getLogger(PgVectorProblemReferenceRetrievalAdapter.class);
    private final ProblemSearchDocumentFactory documentFactory;
    private final EmbeddingClient embeddingClient;
    private final ProblemReferenceJdbcRepository repository;
    private final DeterministicMmrSelector selector;
    private final ProblemRagProperties properties;
    private final ProblemRetrievalTraceJdbcRepository trace;
    private final ExecutorService executor;

    public PgVectorProblemReferenceRetrievalAdapter(ProblemSearchDocumentFactory documentFactory, EmbeddingClient embeddingClient,
            ProblemReferenceJdbcRepository repository, DeterministicMmrSelector selector, ProblemRagProperties properties,
            ProblemRetrievalTraceJdbcRepository trace, @Qualifier("problemRagSearchExecutor") ExecutorService executor) {
        this.documentFactory = documentFactory; this.embeddingClient = embeddingClient; this.repository = repository;
        this.selector = selector; this.properties = properties; this.trace = trace; this.executor = executor;
    }

    /** 검색 Provider·DB 실패를 생성 경로에 전파하지 않고 fallback으로 반환한다. */
    @Override
    public List<RetrievedProblemReference> retrieve(ProblemReferenceQuery query) {
        log.info("pgvector Retrieval 시작 — requestId={} purpose={} subUnitId={} excludedCount={}",
                query.retrievalRequestId(), query.purpose(), query.curriculum().subUnitId(), query.excludedQuestionIds().size());
        trace.insertStarted(query, properties.policyVersion());
        Future<List<RetrievedProblemReference>> future = executor.submit(() -> retrieveInternal(query));
        try { return future.get(properties.searchTimeout().toMillis(), TimeUnit.MILLISECONDS); }
        catch (TimeoutException e) { future.cancel(true); trace.complete(query.retrievalRequestId(), 0, 0, RetrievalFallbackReason.SEARCH_TIMEOUT, properties.searchTimeout().toMillis()); return List.of(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); trace.complete(query.retrievalRequestId(), 0, 0, RetrievalFallbackReason.SEARCH_TIMEOUT, 0); return List.of(); }
        catch (java.util.concurrent.ExecutionException e) { log.warn("pgvector Retrieval 실행 실패 — requestId={} causeType={} message={}", query.retrievalRequestId(), e.getCause() == null ? "unknown" : e.getCause().getClass().getSimpleName(), e.getCause() == null ? e.getMessage() : e.getCause().getMessage()); trace.complete(query.retrievalRequestId(), 0, 0, RetrievalFallbackReason.PROVIDER_FAILURE, 0); return List.of(); }
        catch (RuntimeException e) { log.warn("pgvector Retrieval 호출 실패 — requestId={} exceptionType={} message={}", query.retrievalRequestId(), e.getClass().getSimpleName(), e.getMessage()); trace.complete(query.retrievalRequestId(), 0, 0, RetrievalFallbackReason.PROVIDER_FAILURE, 0); return List.of(); }
    }

    private List<RetrievedProblemReference> retrieveInternal(ProblemReferenceQuery query) {
        String text = documentFactory.createQuery(query);
        var embedding = embeddingClient.embed(text);
        log.info("pgvector Retrieval 임베딩 완료 — requestId={} dimensions={}", query.retrievalRequestId(), embedding.vector().size());
        List<ProblemSearchCandidate> candidates = repository.findCandidates(query, VectorCodec.encode(embedding.vector()));
        log.info("pgvector Retrieval 후보 조회 완료 — requestId={} candidateCount={}", query.retrievalRequestId(), candidates.size());
        if (candidates.isEmpty()) { trace.complete(query.retrievalRequestId(), 0, 0, RetrievalFallbackReason.NO_CANDIDATES, 0); return List.of(); }
        double lambda = query.purpose() == com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose.PERSONALIZED_APPLICATION
                ? properties.applicationLambda() : properties.defaultLambda();
        List<ProblemSearchCandidate> selected = selector.select(candidates, embedding.vector(), query.selectionLimit(), lambda, query.questionType(), query.difficulty());
        trace.insertCandidates(query.retrievalRequestId(), candidates, selected.stream().map(ProblemSearchCandidate::questionId).collect(java.util.stream.Collectors.toSet()));
        return selected.stream().map(c -> new RetrievedProblemReference(c.questionId(), c.snapshot(), c.denseScore(), c.denseRank(), c.documentHash(), c.duplicateClusterKey(), java.util.Set.of())).toList();
    }
}
