package com.cenedu.backend.infra.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.support.PostgresTestcontainer;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        "app.problem.rag.enabled=true",
        "app.problem.rag.indexing.enabled=false"
})
@Import(PostgresTestcontainer.class)
class PgVectorProblemReferenceRetrievalAdapterIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProblemReferenceJdbcRepository repository;

    @BeforeEach
    void cleanRows() {
        jdbc.update("DELETE FROM problem_search_index");
    }

    @Test
    void appliesMetadataFiltersExclusionAndRestoresSnapshot() {
        insert(701L, "9수01-01", 30L, "SHORT_INPUT", "mid", false, unitVector());
        insert(702L, "9수01-02", 30L, "SHORT_INPUT", "mid", false, unitVector());
        insert(703L, "9수01-01", 31L, "SHORT_INPUT", "mid", false, unitVector());
        insert(704L, "9수01-01", 30L, "SHORT_INPUT", "high", false, unitVector());
        insert(705L, "9수01-01", 30L, "SHORT_INPUT", "mid", true, unitVector());

        ProblemReferenceQuery query = new ProblemReferenceQuery(UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, scope("9수01-01", 30L),
                QuestionType.SHORT_INPUT, "mid", null, null, 40, 3, Set.of(701L));

        List<ProblemSearchCandidate> candidates = repository.findCandidates(query, unitVector());

        assertThat(candidates).extracting(ProblemSearchCandidate::questionId)
                .containsExactly(703L, 704L);
        assertThat(candidates.getFirst().snapshot()).isInstanceOf(QuestionSnapshotV1.class);
    }

    private void insert(long id, String achievement, long subUnit, String type, String difficulty,
                        boolean deleted, String vector) {
        jdbc.update("""
                INSERT INTO problem_search_index(question_id, curriculum_revision, school_level, grade,
                    semester, achievement_standard_id, sub_unit_id, question_type, difficulty, presentation,
                    source_family_key, document_text, document_hash, duplicate_cluster_key, concept_keys,
                    snapshot, embedding_model, embedding_dimensions, embedding, index_status, deleted)
                VALUES (?, '2022_REVISED', 'MIDDLE', 1, 1, ?, ?, ?, ?, 'TEXT_ONLY', ?, 'safe document',
                    repeat('a', 64), repeat('b', 64), '{}', CAST(? AS jsonb), 'test', 1024,
                    CAST(? AS vector), 'READY', ?)
                """, id, achievement, subUnit, type, difficulty, "family:" + id, snapshot(id), vector, deleted);
    }

    private static CurriculumScope scope(String achievement, long subUnit) {
        return new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, achievement, subUnit,
                "수와 연산", "정수와 유리수", "정수");
    }

    private static String snapshot(long id) {
        return "{\"schemaVersion\":1,\"metadata\":{\"questionType\":\"SHORT_INPUT\",\"presentation\":\"TEXT_ONLY\",\"difficulty\":\"mid\",\"subUnitId\":30,\"topicCode\":null,\"evaluationArea\":null,\"derivedFromQuestionId\":null},\"contentBlocks\":[],\"assets\":[],\"choices\":[],\"steps\":[],\"answerUnits\":[],\"explanation\":null,\"learningGuide\":null,\"rubricItems\":[]}";
    }

    private static String unitVector() {
        return "[1," + "0,".repeat(1022) + "0]";
    }
}
