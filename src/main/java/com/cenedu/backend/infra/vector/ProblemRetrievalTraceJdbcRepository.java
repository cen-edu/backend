package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.RetrievalFallbackReason;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProblemRetrievalTraceJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProblemRetrievalTraceJdbcRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 검색 시작을 원문 없이 기록한다. */
    public void insertStarted(ProblemReferenceQuery query, String policyVersion) {
        jdbc.update("INSERT INTO problem_retrieval_trace(id,request_id,purpose) VALUES (:id,:request,:purpose) ON CONFLICT DO NOTHING",
                new MapSqlParameterSource().addValue("id", query.retrievalRequestId()).addValue("request", query.retrievalRequestId()).addValue("purpose", policyVersion));
    }
    /** dense 후보와 선택 여부를 원문 없이 기록한다. */
    public void insertCandidates(UUID requestId, List<ProblemSearchCandidate> candidates, Set<Long> selected) {
        for (int i = 0; i < candidates.size(); i++) {
            ProblemSearchCandidate c = candidates.get(i);
            jdbc.update("INSERT INTO problem_retrieval_candidate(trace_id,question_id,dense_rank,dense_score,selected,duplicate_cluster_key,source_family_key) VALUES (:trace,:question,:rank,:score,:selected,:cluster,:family)",
                    new MapSqlParameterSource().addValue("trace", requestId).addValue("question", c.questionId()).addValue("rank", c.denseRank()).addValue("score", c.denseScore()).addValue("selected", selected.contains(c.questionId())).addValue("cluster", c.duplicateClusterKey()).addValue("family", c.sourceFamilyKey()));
        }
    }
    /** 검색 완료 수치와 fallback 사유를 기록한다. */
    public void complete(UUID requestId, int candidateCount, int selectedCount, RetrievalFallbackReason reason, long durationMs) { }
    /** 생성 Job/Item 연결은 후속 schema 확장 시 반영한다. */
    public void linkGeneration(UUID requestId, long jobId, long itemId) { }
    /** Authoring Version 연결은 후속 schema 확장 시 반영한다. */
    public void linkAuthoringVersion(UUID requestId, long authoringVersionId) { }
}
