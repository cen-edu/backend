package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemRetrievalTracePort;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievalFallbackReason;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PgVectorProblemRetrievalTraceAdapter implements ProblemRetrievalTracePort {
    private final ProblemRetrievalTraceJdbcRepository repository;
    public PgVectorProblemRetrievalTraceAdapter(ProblemRetrievalTraceJdbcRepository repository) { this.repository = repository; }
    /** 검색 실패를 원문 없이 retrieval trace에 기록한다. */
    @Override public void recordFallback(ProblemReferenceQuery query, RetrievalFallbackReason reason) { repository.insertStarted(query, reason.name()); repository.complete(query.retrievalRequestId(), 0, 0, reason, 0); }
    /** 생성 Job/Item을 retrieval trace에 연결한다. */
    @Override public void linkGeneration(UUID id, long jobId, long itemId) { repository.linkGeneration(id, jobId, itemId); }
    /** Authoring Version을 retrieval trace에 연결한다. */
    @Override public void linkAuthoringVersion(UUID id, long versionId) { repository.linkAuthoringVersion(id, versionId); }
}
