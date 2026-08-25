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

    /** 새 명령을 등록하거나 종료된 동일 스키마 작업의 내용이 달라졌으면 다시 PENDING으로 만든다. */
    public boolean insertPending(SearchIndexingCommand command) {
        String json;
        try { json = objectMapper.writeValueAsString(command); }
        catch (Exception e) { throw new IllegalArgumentException("검색 인덱싱 명령을 직렬화할 수 없습니다.", e); }
        int count = jdbc.update("""
                INSERT INTO problem_search_index_task AS current_task
                    (question_id, index_schema_version, idempotency_key, command, status, next_attempt_at)
                VALUES (:questionId, :schemaVersion, :key, CAST(:command AS jsonb), 'PENDING', CURRENT_TIMESTAMP)
                ON CONFLICT (question_id, index_schema_version) DO UPDATE
                SET command=EXCLUDED.command, idempotency_key=EXCLUDED.idempotency_key,
                    status='PENDING', attempt_count=0, next_attempt_at=CURRENT_TIMESTAMP,
                    last_error=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE current_task.status IN ('READY','SKIPPED','FAILED')
                  AND current_task.command IS DISTINCT FROM EXCLUDED.command
                """, new MapSqlParameterSource().addValue("questionId", command.questionId())
                .addValue("schemaVersion", command.indexSchemaVersion())
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
        List<ReadySearchIndexMetadata> result = jdbc.query("SELECT document_hash, index_schema_version FROM problem_search_index WHERE question_id=:id AND index_status='READY' ORDER BY index_schema_version DESC",
                new MapSqlParameterSource("id", questionId), (rs, row) -> new ReadySearchIndexMetadata(rs.getString(1), rs.getShort(2)));
        return result.stream().findFirst();
    }

    /** 본문 임베딩은 유지하고 변경된 검색 메타데이터와 Snapshot만 갱신한다. */
    public void refreshReadyMetadata(ClaimedSearchIndexTask task, ProblemSearchDocument document) {
        jdbc.update("""
                UPDATE problem_search_index SET curriculum_revision=:revision, school_level=:school,
                    grade=:grade, semester=:semester, achievement_standard_id=:achievement,
                    sub_unit_id=:subUnit, question_type=:type, difficulty=:difficulty,
                    presentation=:presentation, visual_kind=:visualKind,
                    index_schema_version=:schemaVersion, source_family_key=:family,
                    document_text=:text, document_hash=:hash, duplicate_cluster_key=:duplicate,
                    concept_keys=:concepts, snapshot=CAST(:snapshot AS jsonb),
                    index_status='READY', deleted=false, updated_at=CURRENT_TIMESTAMP
                WHERE question_id=:questionId AND index_status='READY'
                """, indexParameters(task, document));
    }

    /** 새 임베딩이 준비된 문서를 READY 행으로 원자 교체한다. */
    public void upsertReady(ClaimedSearchIndexTask task, ProblemSearchDocument document,
                            EmbeddingResult embedding, String vectorLiteral) {
        jdbc.update("""
                INSERT INTO problem_search_index(question_id, curriculum_revision, school_level, grade, semester,
                    achievement_standard_id, sub_unit_id, question_type, difficulty, presentation, visual_kind, index_schema_version, source_family_key,
                    document_text, document_hash, duplicate_cluster_key, concept_keys, snapshot, embedding_model,
                    embedding_dimensions, embedding, index_status, deleted)
                VALUES (:questionId,:revision,:school,:grade,:semester,:achievement,:subUnit,:type,:difficulty,:presentation,:visualKind,:schemaVersion,
                    :family,:text,:hash,:duplicate,:concepts,CAST(:snapshot AS jsonb),:model,:dimensions,
                    CAST(:embedding AS vector),'READY',false)
                ON CONFLICT (question_id) DO UPDATE SET curriculum_revision=EXCLUDED.curriculum_revision,
                    school_level=EXCLUDED.school_level, grade=EXCLUDED.grade, semester=EXCLUDED.semester,
                    achievement_standard_id=EXCLUDED.achievement_standard_id,
                    sub_unit_id=EXCLUDED.sub_unit_id, question_type=EXCLUDED.question_type,
                    difficulty=EXCLUDED.difficulty, presentation=EXCLUDED.presentation,
                    source_family_key=EXCLUDED.source_family_key, document_text=EXCLUDED.document_text,
                    visual_kind=EXCLUDED.visual_kind, index_schema_version=EXCLUDED.index_schema_version,
                    document_hash=EXCLUDED.document_hash, duplicate_cluster_key=EXCLUDED.duplicate_cluster_key,
                    concept_keys=EXCLUDED.concept_keys, snapshot=EXCLUDED.snapshot, embedding_model=EXCLUDED.embedding_model,
                    embedding_dimensions=EXCLUDED.embedding_dimensions, embedding=EXCLUDED.embedding,
                    index_status='READY', deleted=false, updated_at=CURRENT_TIMESTAMP
                """, indexParameters(task, document)
                .addValue("model", embedding.model()).addValue("dimensions", embedding.vector().size())
                .addValue("embedding", vectorLiteral));
    }

    /** 삭제된 원본 문항의 READY 인덱스를 검색 불가 상태로 동기화한다. */
    public int markDeletedSourceIndexes() {
        return jdbc.update("""
                UPDATE problem_search_index search_index
                SET index_status='DELETED', deleted=true, updated_at=CURRENT_TIMESTAMP
                FROM problem_question source_question
                WHERE source_question.id=search_index.question_id
                  AND source_question.deleted_at IS NOT NULL
                  AND (search_index.index_status <> 'DELETED' OR search_index.deleted=false)
                """, new MapSqlParameterSource());
    }

    /** 최신 작업보다 낡거나 비어 있는 활성 문항 인덱스 작업을 다시 PENDING으로 전환한다. */
    public int reactivateStaleTasks() {
        return jdbc.update("""
                UPDATE problem_search_index_task task
                SET status='PENDING', attempt_count=0, next_attempt_at=CURRENT_TIMESTAMP,
                    last_error=NULL, updated_at=CURRENT_TIMESTAMP
                FROM problem_question source_question
                LEFT JOIN problem_search_index search_index
                  ON search_index.question_id=source_question.id
                WHERE source_question.id=task.question_id
                  AND source_question.deleted_at IS NULL
                  AND task.status IN ('READY','SKIPPED')
                  AND NOT EXISTS (
                      SELECT 1 FROM problem_search_index_task newer
                      WHERE newer.question_id=task.question_id
                        AND newer.index_schema_version > task.index_schema_version)
                  AND (search_index.question_id IS NULL
                       OR search_index.index_status <> 'READY'
                       OR search_index.deleted=true
                       OR search_index.index_schema_version < task.index_schema_version
                       OR (search_index.index_schema_version = task.index_schema_version
                           AND search_index.visual_kind <> COALESCE(task.command->>'visualKind', 'NONE')))
                """, new MapSqlParameterSource());
    }

    /** 커서 뒤의 활성·비서술형·미인덱싱 문항 ID를 일정 크기로 반환한다. */
    public List<Long> findActiveMissingQuestionIds(long afterQuestionId, int limit) {
        return jdbc.query("""
                SELECT source_question.id
                FROM problem_question source_question
                LEFT JOIN problem_search_index search_index
                  ON search_index.question_id=source_question.id
                 AND search_index.index_status='READY' AND search_index.deleted=false
                WHERE source_question.deleted_at IS NULL
                  AND source_question.question_type <> 'ESSAY'
                  AND search_index.question_id IS NULL
                  AND source_question.id > :afterQuestionId
                ORDER BY source_question.id
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("afterQuestionId", afterQuestionId)
                .addValue("limit", limit), (rs, row) -> rs.getLong("id"));
    }

    /** 커서 뒤에서 v1 또는 미분류 그림인 활성 인덱스 문항 ID를 반환한다. */
    public List<Long> findVisualReclassificationQuestionIds(long afterQuestionId, int limit) {
        return jdbc.query("""
                SELECT source_question.id
                FROM problem_question source_question
                JOIN problem_search_index search_index
                  ON search_index.question_id=source_question.id
                 AND search_index.index_status='READY' AND search_index.deleted=false
                WHERE source_question.deleted_at IS NULL
                  AND source_question.question_type <> 'ESSAY'
                  AND (search_index.index_schema_version < 2
                       OR search_index.visual_kind='UNKNOWN_FIGURE')
                  AND source_question.id > :afterQuestionId
                ORDER BY source_question.id
                LIMIT :limit
                """, new MapSqlParameterSource().addValue("afterQuestionId", afterQuestionId)
                .addValue("limit", limit), (rs, row) -> rs.getLong("id"));
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
    private MapSqlParameterSource indexParameters(ClaimedSearchIndexTask task,
            ProblemSearchDocument document) {
        return new MapSqlParameterSource().addValue("questionId", task.questionId())
                .addValue("revision", task.command().curriculum().curriculumRevision())
                .addValue("school", task.command().curriculum().schoolLevel())
                .addValue("grade", task.command().curriculum().grade())
                .addValue("semester", task.command().curriculum().semester())
                .addValue("achievement", task.command().curriculum().achievementStandardId())
                .addValue("subUnit", task.command().curriculum().subUnitId())
                .addValue("type", task.command().snapshot().metadata().questionType().name())
                .addValue("difficulty", task.command().snapshot().metadata().difficulty())
                .addValue("presentation", task.command().snapshot().metadata().presentation().name())
                .addValue("visualKind", task.command().visualKind().name())
                .addValue("schemaVersion", task.command().indexSchemaVersion())
                .addValue("family", document.sourceFamilyKey())
                .addValue("text", document.documentText()).addValue("hash", document.documentHash())
                .addValue("duplicate", document.duplicateClusterKey())
                .addValue("concepts", task.command().conceptKeys().toArray(new String[0]))
                .addValue("snapshot", write(task.command().snapshot()));
    }
    private String write(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record ClaimedSearchIndexTask(long taskId, long questionId, SearchIndexingCommand command, int attemptCount) {}
    public record ReadySearchIndexMetadata(String documentHash, short indexSchemaVersion) {}
}
