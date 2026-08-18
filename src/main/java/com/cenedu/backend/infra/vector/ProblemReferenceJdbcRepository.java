package com.cenedu.backend.infra.vector;

import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ProblemReferenceJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    public ProblemReferenceJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) { this.jdbc = jdbc; this.objectMapper = objectMapper; }

    /** metadata hard filter 뒤 cosine 최근접 후보를 dense 순서로 반환한다. */
    public List<ProblemSearchCandidate> findCandidates(ProblemReferenceQuery query, String queryVectorLiteral) {
        boolean hasExcluded = !query.excludedQuestionIds().isEmpty();
        String exclusion = hasExcluded ? "AND question_id NOT IN (:excludedQuestionIds)" : "";
        int difficulty = switch (query.difficulty()) { case "low" -> 1; case "high" -> 3; default -> 2; };
        String sql = """
                WITH nearest AS MATERIALIZED (
                    SELECT *, embedding <=> CAST(:queryVector AS vector) AS cosine_distance
                    FROM problem_search_index
                    WHERE index_status = 'READY' AND deleted = false
                      AND curriculum_revision = :curriculumRevision AND school_level = :schoolLevel AND grade = :grade
                      AND ((:achievementStandardId IS NOT NULL AND achievement_standard_id = :achievementStandardId)
                        OR (:achievementStandardId IS NULL AND sub_unit_id = :subUnitId))
                      AND difficulty BETWEEN :minimumDifficulty AND :maximumDifficulty
                      AND (:allowCrossType OR question_type = :questionType)
                """ + exclusion + """
                    ORDER BY embedding <=> CAST(:queryVector AS vector) LIMIT :candidateLimit)
                SELECT question_id, cosine_distance, duplicate_cluster_key, source_family_key,
                       question_type, difficulty, document_hash, snapshot, embedding::text AS vector_literal
                FROM nearest ORDER BY cosine_distance, question_id
                """;
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("queryVector", queryVectorLiteral)
                .addValue("curriculumRevision", query.curriculum().curriculumRevision()).addValue("schoolLevel", query.curriculum().schoolLevel())
                .addValue("grade", query.curriculum().grade()).addValue("achievementStandardId", query.curriculum().achievementStandardId())
                .addValue("subUnitId", query.curriculum().subUnitId()).addValue("minimumDifficulty", Math.max(1, difficulty - 1))
                .addValue("maximumDifficulty", Math.min(3, difficulty + 1)).addValue("allowCrossType", query.purpose().name().equals("PERSONALIZED_APPLICATION"))
                .addValue("questionType", query.questionType().name()).addValue("candidateLimit", query.candidateLimit());
        if (hasExcluded) params.addValue("excludedQuestionIds", query.excludedQuestionIds());
        return jdbc.query(sql, params, (rs, row) -> {
            try {
                String vector = rs.getString("vector_literal");
                double distance = rs.getDouble("cosine_distance");
                return new ProblemSearchCandidate(rs.getLong("question_id"), row + 1, 1.0 - distance,
                        VectorCodec.decode(vector), rs.getString("duplicate_cluster_key"), rs.getString("source_family_key"),
                        QuestionType.valueOf(rs.getString("question_type")), rs.getString("difficulty"),
                        objectMapper.readValue(rs.getString("snapshot"), QuestionSnapshotV1.class), rs.getString("document_hash"));
            } catch (Exception e) { throw new IllegalStateException("검색 Snapshot 복원에 실패했습니다.", e); }
        });
    }
}
