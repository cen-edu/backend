package com.cenedu.backend.domain.problem.authoring.retrieval;

import java.util.UUID;

public interface ProblemRetrievalTracePort {
    /** 검색 실행 전후 실패를 원문 없이 기록한다. */
    void recordFallback(ProblemReferenceQuery query, RetrievalFallbackReason reason);
    /** 저장 뒤 확보된 생성 Job/Item을 기존 trace에 연결한다. */
    void linkGeneration(UUID retrievalRequestId, long jobId, long itemId);
    /** 생성·검증 뒤 확보된 Authoring Version을 기존 trace에 연결한다. */
    void linkAuthoringVersion(UUID retrievalRequestId, long authoringVersionId);
}
