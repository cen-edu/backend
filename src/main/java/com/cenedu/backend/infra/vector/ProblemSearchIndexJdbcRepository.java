package com.cenedu.backend.infra.vector;

import com.cenedu.backend.ai.embedding.EmbeddingResult;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.search.ProblemSearchDocument;
import com.cenedu.backend.domain.problem.authoring.search.SearchIndexingCommand;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class ProblemSearchIndexJdbcRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ProblemSearchIndexJdbcRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc; this.objectMapper = objectMapper;
    }

    /** questionId 멱등 키로 PENDING 작업을 만들며 이미 존재하면 false를 반환한다. */
    public boolean insertPending(SearchIndexingCommand command) {
        String json;
        try { json = objectMapper.writeValueAsString(command); }
        catch (Exception e) { throw new IllegalArgumentException("검색 인덱싱 명령을 직렬화할 수 없습니다.", e); }
        int count = jdbc.update("""
                INSERT INTO problem_search_index_task(question_id, idempotency_key, command, status, next_attempt_at)
                VALUES (:questionId, :key, CAST(:command AS jsonb), 'PENDING', CURRENT_TIMESTAMP)
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource().addValue("questionId", command.questionId())
                .addValue("key", command.idempotencyKey().toString()).addValue("command", json));
        return count == 1;
    }

    /** 처리 가능한 인덱싱 작업을 원자적으로 선점한다. */
    @Transactional
    public List<ClaimedSearchIndexTask> claimDue(Instant now, int limit) {
        return jdbc.query("""
                UPDATE problem_search_index_task task SET status='PROCESSING', attempt_count=attempt_count+1,
                    updated_at=CURRENT_TIMESTAMP
                WHERE task.id IN (SELECT id FROM problem_search_index_task
                    WHERE (status IN ('PENDING','RETRY_WAIT') AND (next_attempt_at IS NULL OR next_attempt_at <= :now))
                       OR (status='PROCESSING' AND updated_at < :stale)
                    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT :limit)
                RETURNING id, question_id, command, attempt_count
                """, new MapSqlParameterSource().addValue("now", Timestamp.from(now))
                .addValue("stale", Timestamp.from(now.minusSeconds(60)))
                .addValue("limit", limit), (rs, row) -> {
                    try { return new ClaimedSearchIndexTask(rs.getLong("id"), rs.getLong("question_id"),
                            objectMapper.readValue(rs.getString("command"), SearchIndexingCommand.class),
                            rs.getInt("attempt_count")); }
                    catch (Exception e) { throw new IllegalStateException("검색 인덱싱 명령을 읽을 수 없습니다.", e); }
                });
    }

    /** 현재 READY 문서의 해시를 반환한다. */
    public Optional<ReadySearchIndexMetadata> findReadyMetadata(long questionId) {
        List<ReadySearchIndexMetadata> result = jdbc.query("SELECT document_hash FROM problem_search_index WHERE question_id=:id AND index_status='READY'",
                new MapSqlParameterSource("id", questionId), (rs, row) -> new ReadySearchIndexMetadata(rs.getString(1)));
        return result.stream().findFirst();
    }

    /** 새 임베딩이 준비된 문서를 READY 행으로 원자 교체한다. */
    public void upsertReady(ClaimedSearchIndexTask task, ProblemSearchDocument document,
                            EmbeddingResult embedding, String vectorLiteral) {
        jdbc.update("""
                INSERT INTO problem_search_index(question_id, curriculum_revision, school_level, grade, semester,
                    achievement_standard_id, sub_unit_id, question_type, difficulty, presentation, source_family_key,
                    document_text, document_hash, duplicate_cluster_key, concept_keys, snapshot, embedding_model,
                    embedding_dimensions, embedding, index_status, deleted)
                VALUES (:questionId,:revision,:school,:grade,:semester,:achievement,:subUnit,:type,:difficulty,:presentation,
                    :family,:text,:hash,:duplicate,:concepts,CAST(:snapshot AS jsonb),:model,:dimensions,
                    CAST(:embedding AS vector),'READY',false)
                ON CONFLICT (question_id) DO UPDATE SET document_text=EXCLUDED.document_text,
                    document_hash=EXCLUDED.document_hash, duplicate_cluster_key=EXCLUDED.duplicate_cluster_key,
                    concept_keys=EXCLUDED.concept_keys, snapshot=EXCLUDED.snapshot, embedding_model=EXCLUDED.embedding_model,
                    embedding_dimensions=EXCLUDED.embedding_dimensions, embedding=EXCLUDED.embedding,
                    index_status='READY', deleted=false, updated_at=CURRENT_TIMESTAMP
                """, new MapSqlParameterSource().addValue("questionId", task.questionId())
                .addValue("revision", task.command().curriculum().curriculumRevision()).addValue("school", task.command().curriculum().schoolLevel())
                .addValue("grade", task.command().curriculum().grade()).addValue("semester", task.command().curriculum().semester())
                .addValue("achievement", task.command().curriculum().achievementStandardId()).addValue("subUnit", task.command().curriculum().subUnitId())
                .addValue("type", task.command().snapshot().metadata().questionType().name()).addValue("difficulty", task.command().snapshot().metadata().difficulty())
                .addValue("presentation", task.command().snapshot().metadata().presentation().name()).addValue("family", document.sourceFamilyKey())
                .addValue("text", document.documentText()).addValue("hash", document.documentHash()).addValue("duplicate", document.duplicateClusterKey())
                .addValue("concepts", task.command().conceptKeys().toArray(new String[0])).addValue("snapshot", write(task.command().snapshot()))
                .addValue("model", embedding.model()).addValue("dimensions", embedding.vector().size()).addValue("embedding", vectorLiteral));
    }

    /** 작업을 READY 상태로 종료한다. */
    public void markReady(long taskId) { updateStatus(taskId, "READY", null); }
    /** 동일 문서를 다시 계산하지 않고 작업을 SKIPPED 상태로 종료한다. */
    public void markSkipped(long taskId) { updateStatus(taskId, "SKIPPED", null); }
    /** 재시도 가능한 실패를 다음 실행 시각과 함께 기록한다. */
    public void markRetry(long taskId, int attempts, Instant next, String code) { jdbc.update("UPDATE problem_search_index_task SET status='RETRY_WAIT',attempt_count=:attempts,next_attempt_at=:next,last_error=:error,updated_at=CURRENT_TIMESTAMP WHERE id=:id", params(taskId, attempts, next, code)); }
    /** 재시도하지 않을 실패를 FAILED 상태로 기록한다. */
    public void markFailed(long taskId, int attempts, String code) { jdbc.update("UPDATE problem_search_index_task SET status='FAILED',attempt_count=:attempts,last_error=:error,updated_at=CURRENT_TIMESTAMP WHERE id=:id", params(taskId, attempts, null, code)); }

    private void updateStatus(long id, String status, String error) { jdbc.update("UPDATE problem_search_index_task SET status=:status,last_error=:error,updated_at=CURRENT_TIMESTAMP WHERE id=:id", new MapSqlParameterSource().addValue("id", id).addValue("status", status).addValue("error", error)); }
    private MapSqlParameterSource params(long id, int attempts, Instant next, String error) {
        return new MapSqlParameterSource().addValue("id", id).addValue("attempts", attempts)
                .addValue("next", next == null ? null : Timestamp.from(next))
                .addValue("error", error);
    }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record ClaimedSearchIndexTask(long taskId, long questionId, SearchIndexingCommand command, int attemptCount) {}
    public record ReadySearchIndexMetadata(String documentHash) {}
}
