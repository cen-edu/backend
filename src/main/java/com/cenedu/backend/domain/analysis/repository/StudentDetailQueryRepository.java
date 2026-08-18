package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnalysisSummaryRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnswerUnitRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentItemDetailRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentWeakSubcategoryRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 학생 상세 화면의 요약·문항·답안 단위 결과를 읽는 analysis 소유 Repository. */
@Repository
@RequiredArgsConstructor
public class StudentDetailQueryRepository {

    private static final String ITEM_RESULT_CTE = """
            WITH item_definition AS (
                SELECT wi.id AS worksheet_item_id,
                       wi.display_order AS item_number,
                       wi.max_score,
                       pq.id AS question_id,
                       pq.question_type,
                       pq.evaluation_area,
                       pq.difficulty,
                       pq.sub_unit_id,
                       cu.name AS subcategory_name,
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
                JOIN curriculum_unit cu ON cu.id = pq.sub_unit_id
                WHERE wa.id = :assignmentId
                  AND pq.deleted_at IS NULL
            ),
            raw_item_result AS (
                SELECT was.id AS assignment_student_id,
                       was.student_id,
                       ma.name AS student_name,
                       item.worksheet_item_id,
                       item.item_number,
                       item.max_score AS stored_max_score,
                       item.question_id,
                       item.question_type,
                       item.evaluation_area,
                       item.difficulty,
                       item.sub_unit_id,
                       item.subcategory_name,
                       item.question_title,
                       COUNT(pau.id) AS expected_unit_count,
                       COUNT(sa.id) FILTER (
                           WHERE sa.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       COUNT(sa.id) FILTER (
                           WHERE sa.grading_status = 'FAILED'
                       ) AS failed_unit_count,
                       COALESCE(SUM(sa.final_score) FILTER (
                           WHERE sa.grading_status = 'GRADED'
                       ), 0) AS graded_score,
                       sqt.time_spent_seconds
                FROM worksheet_assignment_student was
                JOIN member_account ma ON ma.id = was.student_id
                CROSS JOIN item_definition item
                JOIN problem_answer_unit pau ON pau.question_id = item.question_id
                LEFT JOIN submission_answer sa
                  ON sa.assignment_student_id = was.id
                 AND sa.answer_unit_id = pau.id
                LEFT JOIN submission_question_time sqt
                  ON sqt.assignment_student_id = was.id
                 AND sqt.worksheet_item_id = item.worksheet_item_id
                WHERE was.assignment_id = :assignmentId
                GROUP BY was.id, was.student_id, ma.name,
                         item.worksheet_item_id, item.item_number,
                         item.max_score, item.question_id, item.question_type,
                         item.evaluation_area, item.difficulty, item.sub_unit_id,
                         item.subcategory_name, item.question_title,
                         sqt.time_spent_seconds
            ),
            item_result AS (
                SELECT *,
                       COALESCE(stored_max_score, expected_unit_count::numeric)
                           AS resolved_max_score,
                       CASE
                           WHEN expected_unit_count > 0
                            AND graded_unit_count = expected_unit_count
                           THEN graded_score
                           ELSE NULL
                       END AS score,
                       CASE
                           WHEN expected_unit_count > 0
                            AND graded_unit_count = expected_unit_count
                            AND graded_score = COALESCE(
                                stored_max_score, expected_unit_count::numeric
                            )
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct,
                       CASE
                           WHEN expected_unit_count > 0
                            AND graded_unit_count = expected_unit_count
                           THEN 'GRADED'
                           WHEN failed_unit_count > 0
                           THEN 'FAILED'
                           ELSE 'NOT_GRADED'
                       END AS item_grading_status,
                       CASE
                           WHEN expected_unit_count = 0
                             OR graded_unit_count <> expected_unit_count
                           THEN 'NOT_GRADED'
                           WHEN graded_score = COALESCE(
                               stored_max_score, expected_unit_count::numeric
                           )
                           THEN 'CORRECT'
                           WHEN graded_score = 0
                           THEN 'INCORRECT'
                           ELSE 'PARTIAL_CORRECT'
                       END AS result_type
                FROM raw_item_result
            )
            """;

    private final JdbcClient jdbcClient;

    /** 학생의 선택 학습지 수행 회차 ID를 반환한다. */
    public Optional<Long> findAssignmentStudentId(long assignmentId, long studentId) {
        return jdbcClient.sql("""
                        SELECT id
                        FROM worksheet_assignment_student
                        WHERE assignment_id = :assignmentId
                          AND student_id = :studentId
                        """)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query(Long.class)
                .optional();
    }

    /** 선택 학생의 문항 수·정답률·총시간과 학급 정답률을 반환한다. */
    public StudentAnalysisSummaryRow findSummary(long assignmentId, long studentId) {
        String sql = ITEM_RESULT_CTE + """
                , student_duration AS (
                    SELECT student_id,
                           SUM(time_spent_seconds) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           ) AS total_time_spent_seconds
                    FROM item_result
                    GROUP BY student_id
                    HAVING COUNT(time_spent_seconds) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           ) > 0
                )
                SELECT MAX(student_name) FILTER (
                           WHERE student_id = :studentId
                       ) AS student_name,
                       COUNT(*) FILTER (
                           WHERE student_id = :studentId
                       ) AS total_item_count,
                       COUNT(*) FILTER (
                           WHERE student_id = :studentId
                             AND graded_unit_count = expected_unit_count
                       ) AS graded_item_count,
                       COUNT(*) FILTER (
                           WHERE student_id = :studentId AND is_correct
                       ) AS correct_item_count,
                       ROUND(
                           100.0 * COUNT(*) FILTER (
                               WHERE student_id = :studentId AND is_correct
                           )
                           / NULLIF(COUNT(*) FILTER (
                               WHERE student_id = :studentId
                                 AND graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS accuracy_rate,
                       ROUND(
                           100.0 * COUNT(*) FILTER (WHERE is_correct)
                           / NULLIF(COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS class_accuracy_rate,
                       ROUND(
                           100.0 * SUM(score) FILTER (
                               WHERE student_id = :studentId
                                 AND graded_unit_count = expected_unit_count
                           )
                           / NULLIF(SUM(resolved_max_score) FILTER (
                               WHERE student_id = :studentId
                                 AND graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS score_rate,
                       ROUND(
                           100.0 * SUM(score) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           )
                           / NULLIF(SUM(resolved_max_score) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS class_score_rate,
                       CASE
                           WHEN COUNT(time_spent_seconds) FILTER (
                               WHERE student_id = :studentId
                                 AND graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           ) > 0
                           THEN SUM(time_spent_seconds) FILTER (
                               WHERE student_id = :studentId
                                 AND graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           )::bigint * 1000
                           ELSE NULL
                       END AS total_solving_duration_ms,
                       (
                           SELECT ROUND(AVG(total_time_spent_seconds))::bigint * 1000
                           FROM student_duration
                       ) AS class_average_solving_duration_ms
                FROM item_result
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new StudentAnalysisSummaryRow(
                        rs.getString("student_name"),
                        rs.getInt("total_item_count"),
                        rs.getInt("graded_item_count"),
                        rs.getInt("correct_item_count"),
                        rs.getObject("accuracy_rate", BigDecimal.class),
                        rs.getObject("class_accuracy_rate", BigDecimal.class),
                        rs.getObject("score_rate", BigDecimal.class),
                        rs.getObject("class_score_rate", BigDecimal.class),
                        rs.getObject("total_solving_duration_ms", Long.class),
                        rs.getObject(
                                "class_average_solving_duration_ms", Long.class)))
                .single();
    }

    /** 선택 학생의 정답률이 60% 미만인 소분류를 낮은 정답률 순으로 반환한다. */
    public List<StudentWeakSubcategoryRow> findWeakSubcategories(
            long assignmentId,
            long studentId
    ) {
        String sql = ITEM_RESULT_CTE + """
                SELECT sub_unit_id AS subcategory_id,
                       subcategory_name,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) - COUNT(*) FILTER (WHERE is_correct) AS incorrect_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_count,
                       ROUND(
                           100.0 * COUNT(*) FILTER (WHERE is_correct)
                           / NULLIF(COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ), 0),
                           1
                       ) AS accuracy_rate
                FROM item_result
                WHERE student_id = :studentId
                GROUP BY sub_unit_id, subcategory_name
                HAVING COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) > 0
                   AND 100.0 * COUNT(*) FILTER (WHERE is_correct)
                       / COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) < 60
                ORDER BY accuracy_rate ASC, incorrect_count DESC, sub_unit_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new StudentWeakSubcategoryRow(
                        rs.getLong("subcategory_id"),
                        rs.getString("subcategory_name"),
                        rs.getInt("incorrect_count"),
                        rs.getInt("graded_count"),
                        rs.getObject("accuracy_rate", BigDecimal.class)))
                .list();
    }

    /** 선택 학생의 문항 결과와 문항별 학급 정답률·중앙시간을 반환한다. */
    public List<StudentItemDetailRow> findItems(long assignmentId, long studentId) {
        String sql = ITEM_RESULT_CTE + """
                , class_item_result AS (
                    SELECT worksheet_item_id,
                           COUNT(*) FILTER (WHERE is_correct)
                               AS correct_student_count,
                           COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ) AS graded_student_count,
                           ROUND(
                               100.0 * COUNT(*) FILTER (WHERE is_correct)
                               / NULLIF(COUNT(*) FILTER (
                                   WHERE graded_unit_count = expected_unit_count
                               ), 0),
                               1
                           ) AS class_accuracy_rate,
                           ROUND(PERCENTILE_CONT(0.5) WITHIN GROUP (
                               ORDER BY time_spent_seconds
                           ) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND time_spent_seconds IS NOT NULL
                           ))::bigint * 1000 AS class_median_solving_duration_ms
                    FROM item_result
                    GROUP BY worksheet_item_id
                )
                SELECT item.worksheet_item_id,
                       item.question_id,
                       item.item_number,
                       item.question_title,
                       item.question_type,
                       item.evaluation_area,
                       item.difficulty,
                       item.item_grading_status,
                       item.result_type,
                       item.score,
                       item.resolved_max_score,
                       item.time_spent_seconds::bigint * 1000 AS solving_duration_ms,
                       class_result.class_median_solving_duration_ms,
                       class_result.correct_student_count,
                       class_result.graded_student_count,
                       class_result.class_accuracy_rate
                FROM item_result item
                JOIN class_item_result class_result
                  ON class_result.worksheet_item_id = item.worksheet_item_id
                WHERE item.student_id = :studentId
                ORDER BY item.item_number, item.worksheet_item_id
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new StudentItemDetailRow(
                        rs.getLong("worksheet_item_id"),
                        rs.getLong("question_id"),
                        rs.getInt("item_number"),
                        rs.getString("question_title"),
                        rs.getString("question_type"),
                        rs.getString("evaluation_area"),
                        rs.getInt("difficulty"),
                        GradingStatus.valueOf(rs.getString("item_grading_status")),
                        StudentItemResultType.valueOf(rs.getString("result_type")),
                        rs.getObject("score", BigDecimal.class),
                        rs.getObject("resolved_max_score", BigDecimal.class),
                        rs.getObject("solving_duration_ms", Long.class),
                        rs.getObject("class_median_solving_duration_ms", Long.class),
                        rs.getInt("correct_student_count"),
                        rs.getInt("graded_student_count"),
                        rs.getObject("class_accuracy_rate", BigDecimal.class)))
                .list();
    }

    /** 선택 학생의 문항별 답안 단위 응답과 정답을 표시 순서대로 반환한다. */
    public List<StudentAnswerUnitRow> findAnswerUnits(long assignmentId, long studentId) {
        return jdbcClient.sql("""
                        SELECT wi.id AS worksheet_item_id,
                               pau.id AS answer_unit_id,
                               pau.display_order,
                               COALESCE(pau.label, ps.label, pau.unit_key) AS label,
                               pau.diagnostic_type,
                               COALESCE(sa.grading_status, 'NOT_GRADED')
                                   AS grading_status,
                               CASE
                                   WHEN pq.question_type = 'MULTIPLE_CHOICE'
                                   THEN selected_choice.content
                                   ELSE sa.raw_latex
                               END AS student_answer,
                               CASE
                                   WHEN pq.question_type = 'MULTIPLE_CHOICE'
                                   THEN correct_choice.content
                                   ELSE pau.answer_raw
                               END AS correct_answer,
                               CASE
                                   WHEN sa.grading_status = 'GRADED'
                                   THEN sa.final_score
                                   ELSE NULL
                               END AS score,
                               CASE
                                   WHEN sa.grading_status <> 'GRADED' OR sa.id IS NULL
                                   THEN 'NOT_GRADED'
                                   WHEN sa.final_score >= COALESCE(
                                       wi.max_score / NULLIF(unit_count.total_units, 0), 1
                                   )
                                   THEN 'CORRECT'
                                   WHEN sa.final_score = 0
                                   THEN 'INCORRECT'
                                   ELSE 'PARTIAL_CORRECT'
                               END AS result_type
                        FROM worksheet_assignment wa
                        JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                        JOIN problem_question pq ON pq.id = wi.question_id
                        JOIN problem_answer_unit pau ON pau.question_id = pq.id
                        LEFT JOIN problem_step ps ON ps.id = pau.step_id
                        JOIN worksheet_assignment_student was
                          ON was.assignment_id = wa.id
                         AND was.student_id = :studentId
                        LEFT JOIN submission_answer sa
                          ON sa.assignment_student_id = was.id
                         AND sa.answer_unit_id = pau.id
                        LEFT JOIN problem_choice selected_choice
                          ON selected_choice.id = sa.selected_choice_id
                        LEFT JOIN LATERAL (
                            SELECT choice.content
                            FROM problem_choice choice
                            WHERE choice.question_id = pq.id
                              AND pau.answer_raw ~ '^[0-9]+$'
                              AND choice.display_order + 1 = pau.answer_raw::integer
                            LIMIT 1
                        ) correct_choice ON TRUE
                        JOIN LATERAL (
                            SELECT COUNT(*)::numeric AS total_units
                            FROM problem_answer_unit counted_unit
                            WHERE counted_unit.question_id = pq.id
                        ) unit_count ON TRUE
                        WHERE wa.id = :assignmentId
                          AND pq.deleted_at IS NULL
                        ORDER BY wi.display_order, wi.id,
                                 pau.display_order, pau.id
                        """)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new StudentAnswerUnitRow(
                        rs.getLong("worksheet_item_id"),
                        rs.getLong("answer_unit_id"),
                        rs.getInt("display_order"),
                        rs.getString("label"),
                        rs.getString("diagnostic_type"),
                        GradingStatus.valueOf(rs.getString("grading_status")),
                        rs.getString("student_answer"),
                        rs.getString("correct_answer"),
                        rs.getObject("score", BigDecimal.class),
                        StudentItemResultType.valueOf(rs.getString("result_type"))))
                .list();
    }
}
