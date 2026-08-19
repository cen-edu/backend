package com.cenedu.backend.infra.vector;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
class ProblemSearchSchemaMigrationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void createsVector1024HnswAndTelemetryTables() {
        String vectorType = jdbc.queryForObject("""
                SELECT format_type(a.atttypid, a.atttypmod)
                FROM pg_attribute a
                WHERE a.attrelid = 'problem_search_index'::regclass
                  AND a.attname = 'embedding'
                """, String.class);
        String indexDefinition = jdbc.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE indexname = 'idx_problem_search_index_embedding_hnsw'
                """, String.class);

        assertThat(vectorType).isEqualTo("vector(1024)");
        assertThat(indexDefinition).contains("USING hnsw", "vector_cosine_ops");
        assertThat(jdbc.queryForObject("SELECT to_regclass('problem_retrieval_trace')", String.class))
                .isEqualTo("problem_retrieval_trace");
        assertThat(jdbc.queryForObject("SELECT to_regclass('problem_teacher_decision_event')", String.class))
                .isEqualTo("problem_teacher_decision_event");
    }
}
