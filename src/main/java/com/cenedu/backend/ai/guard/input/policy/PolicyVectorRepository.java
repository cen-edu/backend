package com.cenedu.backend.ai.guard.input.policy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 가드레일 소유 테이블에 정책 본문과 벡터를 하나의 행으로 저장한다. */
@Repository
public class PolicyVectorRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PolicyVectorRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** 현재 정책 버전 중 이미 영속 저장된 키를 반환한다. */
    public Set<String> existing(List<String> keys) {
        if (keys.isEmpty()) return Set.of();
        return new HashSet<>(jdbc.queryForList(
                "SELECT version_key FROM guard_policy_embedding WHERE version_key IN (:keys)",
                Map.of("keys", keys), String.class));
    }

    /** 임베딩 성공 후 본문과 벡터를 원자적으로 저장한다. 동시 중복 삽입은 무시한다. */
    public void insert(String key, String hash, String model, PolicyCatalog.Policy policy, String vector) {
        jdbc.update("""
                INSERT INTO guard_policy_embedding
                  (version_key, policy_id, scope, effect, message, body, content_hash, embedding_model, embedding)
                VALUES (:key, :id, :scope, :effect, :message, :body, :hash, :model, CAST(:vector AS vector))
                ON CONFLICT (version_key) DO NOTHING
                """, Map.of("key", key, "id", policy.id(), "scope", policy.scope(), "effect", policy.effect(),
                        "message", policy.message(), "body", policy.body(), "hash", hash, "model", model, "vector", vector));
    }

    /** 현재 파일 버전·모델·페이지로 후보를 제한한 뒤 pgvector 거리로 상위 정책을 찾는다. */
    public List<PolicyRetriever.Hit> search(List<String> keys, String scope, String model, String vector, int limit) {
        if (keys.isEmpty()) return List.of();
        return jdbc.query("""
                SELECT policy_id, scope, effect, message, body,
                       1 - (embedding <=> CAST(:vector AS vector)) AS score
                FROM guard_policy_embedding
                WHERE version_key IN (:keys) AND scope = :scope AND embedding_model = :model
                ORDER BY embedding <=> CAST(:vector AS vector), policy_id
                LIMIT :limit
                """, Map.of("keys", keys, "scope", scope, "model", model, "vector", vector, "limit", limit),
                (rs, row) -> new PolicyRetriever.Hit(new PolicyCatalog.Policy(rs.getString("policy_id"),
                        rs.getString("scope"), rs.getString("effect"), rs.getString("message"), rs.getString("body")),
                        rs.getDouble("score")));
    }
}
