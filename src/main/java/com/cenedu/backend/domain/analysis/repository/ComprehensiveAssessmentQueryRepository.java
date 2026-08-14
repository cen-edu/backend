package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.AssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentItemColumnRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentStudentItemRow;
import com.cenedu.backend.domain.analysis.repository.row.ScoreTimeStudentRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 종합평가 학급 화면의 유형·난이도·문항·점수·시간을 읽는 조회 Repository. */
@Repository
@RequiredArgsConstructor
public class ComprehensiveAssessmentQueryRepository {

    private static final String ITEM_RESULT_CTE = """
            WITH item_definition AS (
                SELECT wi.id AS worksheet_item_id,
                       wi.display_order AS item_number,
                       wi.max_score,
                       pq.id AS question_id,
                       pq.question_type,
                       pq.difficulty,
                       COALESCE(
                           NULLIF((
                               SELECT block ->> 'text'
                               FROM jsonb_array_elements(pq.content_blocks) AS block
                               WHERE block ->> 'blockKind' = 'TEXT'
                               ORDER BY COALESCE(
                                   (block ->> 'displayOrder')::integer, 0
                               )
                               LIMIT 1
                           ), ''),
                           pq.prompt_text
                       ) AS question_title
                FROM worksheet_assignment wa
                JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                JOIN problem_question pq ON pq.id = wi.question_id
                WHERE wa.id = :assignmentId
                  AND pq.deleted_at IS NULL
            ),
            item_result AS (
                SELECT was.id AS assignment_student_id,
                       was.student_id,
                       ma.name AS student_name,
                       item.worksheet_item_id,
                       item.item_number,
                       item.max_score,
                       item.question_type,
                       item.difficulty,
                       item.question_title,
                       COUNT(pau.id) AS expected_unit_count,
                       COUNT(sa.id) FILTER (
                           WHERE sa.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       COUNT(sa.id) FILTER (
                           WHERE sa.grading_status = 'FAILED'
                       ) AS failed_unit_count,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(sa.id) FILTER (
                                WHERE sa.grading_status = 'GRADED'
                            ) = COUNT(pau.id)
                           THEN COALESCE(SUM(sa.final_score) FILTER (
                               WHERE sa.grading_status = 'GRADED'
                           ), 0)
                           ELSE NULL
                       END AS score,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(sa.id) FILTER (
                                WHERE sa.grading_status = 'GRADED'
                            ) = COUNT(pau.id)
                            AND COALESCE(SUM(sa.final_score) FILTER (
                                WHERE sa.grading_status = 'GRADED'
                            ), 0) = item.max_score
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct,
                       sqt.time_spent_seconds
                FROM worksheet_assignment_student was
                JOIN member_account ma ON ma.id = was.student_id
                CROSS JOIN item_definition item
                JOIN problem_answer_unit pau
                  ON pau.question_id = item.question_id
                LEFT JOIN submission_answer sa
                  ON sa.assignment_student_id = was.id
                 AND sa.answer_unit_id = pau.id
                LEFT JOIN submission_question_time sqt
                  ON sqt.assignment_student_id = was.id
                 AND sqt.worksheet_item_id = item.worksheet_item_id
                WHERE was.assignment_id = :assignmentId
                GROUP BY was.id, was.student_id, ma.name,
                         item.worksheet_item_id, item.item_number, item.max_score,
                         item.question_type, item.difficulty, item.question_title,
                         sqt.time_spent_seconds
            )
            """;

    private final JdbcClient jdbcClient;

    /** 객관식·주관식·서술형과 상·중·하별 문항 수와 완전정답률을 반환한다. */
    public List<AssessmentGroupAggregateRow> findGroupAggregates(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                , classified_result AS (
                    SELECT *,
                           CASE question_type
                               WHEN 'MULTIPLE_CHOICE' THEN 'MULTIPLE_CHOICE'
                               WHEN 'ESSAY' THEN 'ESSAY'
                               ELSE 'SHORT_ANSWER'
                           END AS question_type_group,
                           CASE difficulty
                               WHEN 1 THEN 'LOW'
                               WHEN 2 THEN 'MID'
                               WHEN 3 THEN 'HIGH'
                           END AS difficulty_band
                    FROM item_result
                )
                SELECT 'QUESTION_TYPE' AS dimension,
                       question_type_group AS group_code,
                       COUNT(DISTINCT worksheet_item_id) AS item_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_result_count,
                       ROUND(
                           100.0 * COUNT(*) FILTER (WHERE is_correct)
                           / NULLIF(COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS accuracy_rate
                FROM classified_result
                GROUP BY question_type_group
                UNION ALL
                SELECT 'DIFFICULTY' AS dimension,
                       difficulty_band AS group_code,
                       COUNT(DISTINCT worksheet_item_id) AS item_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_result_count,
                       ROUND(
                           100.0 * COUNT(*) FILTER (WHERE is_correct)
                           / NULLIF(COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS accuracy_rate
                FROM classified_result
                GROUP BY difficulty_band
                ORDER BY dimension, group_code
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new AssessmentGroupAggregateRow(
                        AssessmentGroupAggregateRow.GroupDimension.valueOf(
                                rs.getString("dimension")),
                        rs.getString("group_code"),
                        rs.getInt("item_count"),
                        rs.getInt("graded_result_count"),
                        rs.getObject("accuracy_rate", BigDecimal.class)))
                .list();
    }

    /** 채점 결과가 있는 문항 중 완전정답률이 낮은 세 문항을 반환한다. */
    public List<AssessmentPriorityItemRow> findPriorityItems(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                SELECT worksheet_item_id,
                       item_number,
                       question_title,
                       difficulty,
                       COUNT(*) FILTER (WHERE is_correct) AS correct_student_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_student_count
                FROM item_result
                GROUP BY worksheet_item_id, item_number, question_title, difficulty
                HAVING COUNT(*) FILTER (
                    WHERE graded_unit_count = expected_unit_count
                ) > 0
                ORDER BY
                    1.0 * COUNT(*) FILTER (WHERE is_correct)
                    / COUNT(*) FILTER (
                        WHERE graded_unit_count = expected_unit_count
                    ) ASC,
                    item_number ASC
                LIMIT 3
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new AssessmentPriorityItemRow(
                        rs.getLong("worksheet_item_id"),
                        rs.getInt("item_number"),
                        rs.getString("question_title"),
                        rs.getInt("difficulty"),
                        rs.getInt("correct_student_count"),
                        rs.getInt("graded_student_count")))
                .list();
    }

    /** 종합평가 문항 성취 표의 문항 번호와 배점을 반환한다. */
    public List<AssessmentItemColumnRow> findItemColumns(long assignmentId) {
        return jdbcClient.sql("""
                        SELECT wi.id AS worksheet_item_id,
                               wi.display_order AS item_number,
                               wi.max_score
                        FROM worksheet_assignment wa
                        JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                        JOIN problem_question pq ON pq.id = wi.question_id
                        WHERE wa.id = :assignmentId
                          AND pq.deleted_at IS NULL
                        ORDER BY wi.display_order ASC, wi.id ASC
                        """)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new AssessmentItemColumnRow(
                        rs.getLong("worksheet_item_id"),
                        rs.getInt("item_number"),
                        rs.getObject("max_score", BigDecimal.class)))
                .list();
    }

    /** 모든 배정 학생의 문항별 채점 상태·점수·측정 시간을 반환한다. */
    public List<AssessmentStudentItemRow> findStudentItemResults(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                SELECT student_id,
                       student_name,
                       worksheet_item_id,
                       CASE
                           WHEN expected_unit_count > 0
                            AND graded_unit_count = expected_unit_count
                           THEN 'GRADED'
                           WHEN failed_unit_count > 0
                           THEN 'FAILED'
                           ELSE 'NOT_GRADED'
                       END AS grading_status,
                       score,
                       time_spent_seconds::bigint * 1000 AS solving_duration_ms
                FROM item_result
                ORDER BY student_name ASC, student_id ASC,
                         item_number ASC, worksheet_item_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new AssessmentStudentItemRow(
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getLong("worksheet_item_id"),
                        GradingStatus.valueOf(rs.getString("grading_status")),
                        rs.getObject("score", BigDecimal.class),
                        rs.getObject("solving_duration_ms", Long.class)))
                .list();
    }

    /** 학생별 채점 완료 문항 득점률과 해당 문항에서 측정된 총시간을 반환한다. */
    public List<ScoreTimeStudentRow> findScoreTimeStudents(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                SELECT student_id,
                       student_name,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_item_count,
                       ROUND(
                           100.0 * SUM(score) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           )
                           / NULLIF(SUM(max_score) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS score_rate,
                       CASE
                           WHEN COUNT(time_spent_seconds) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           ) > 0
                           THEN SUM(time_spent_seconds) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           )::bigint * 1000
                           ELSE NULL
                       END AS total_solving_duration_ms
                FROM item_result
                GROUP BY student_id, student_name
                ORDER BY student_name ASC, student_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new ScoreTimeStudentRow(
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("graded_item_count"),
                        rs.getObject("score_rate", BigDecimal.class),
                        rs.getObject("total_solving_duration_ms", Long.class)))
                .list();
    }
}
