package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningStudentSubcategoryRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryColumnRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryWeaknessRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 학습평가 학급 화면의 영역·난이도·소분류 성취를 읽는 조회 Repository. */
@Repository
@RequiredArgsConstructor
public class LearningAssessmentQueryRepository {

    private static final String ITEM_RESULT_CTE = """
            WITH item_definition AS (
                SELECT wi.id AS worksheet_item_id,
                       wi.display_order AS item_number,
                       pq.id AS question_id,
                       pq.sub_unit_id,
                       cu.name AS subcategory_name,
                       pq.evaluation_area,
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
                JOIN curriculum_unit cu ON cu.id = pq.sub_unit_id
                WHERE wa.id = :assignmentId
                  AND pq.deleted_at IS NULL
            ),
            item_result AS (
                SELECT was.id AS assignment_student_id,
                       was.student_id,
                       ma.name AS student_name,
                       item.worksheet_item_id,
                       item.item_number,
                       item.sub_unit_id,
                       item.subcategory_name,
                       item.evaluation_area,
                       item.difficulty,
                       item.question_title,
                       COUNT(pau.id) AS expected_unit_count,
                       COUNT(sa.id) FILTER (
                           WHERE sa.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(sa.id) FILTER (
                                WHERE sa.grading_status = 'GRADED'
                            ) = COUNT(pau.id)
                            AND BOOL_AND(COALESCE(sa.final_score, 0) = 1)
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct
                FROM worksheet_assignment_student was
                JOIN member_account ma ON ma.id = was.student_id
                CROSS JOIN item_definition item
                JOIN problem_answer_unit pau
                  ON pau.question_id = item.question_id
                LEFT JOIN submission_answer sa
                  ON sa.assignment_student_id = was.id
                 AND sa.answer_unit_id = pau.id
                WHERE was.assignment_id = :assignmentId
                GROUP BY was.id, was.student_id, ma.name,
                         item.worksheet_item_id, item.item_number,
                         item.sub_unit_id, item.subcategory_name,
                         item.evaluation_area, item.difficulty, item.question_title
            )
            """;

    private final JdbcClient jdbcClient;

    /** 평가 영역과 상·중·하별 문항 수와 채점 완료 결과의 완전정답률을 반환한다. */
    public List<LearningAssessmentGroupAggregateRow> findGroupAggregates(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                , classified_result AS (
                    SELECT *,
                           CASE difficulty
                               WHEN 1 THEN 'LOW'
                               WHEN 2 THEN 'MID'
                               WHEN 3 THEN 'HIGH'
                           END AS difficulty_band
                    FROM item_result
                ),
                group_result AS (
                    SELECT 'EVALUATION_AREA' AS dimension,
                           evaluation_area AS group_code,
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
                    WHERE evaluation_area IS NOT NULL
                    GROUP BY evaluation_area
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
                )
                SELECT dimension, group_code, item_count,
                       graded_result_count, accuracy_rate
                FROM group_result
                ORDER BY dimension, group_code
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new LearningAssessmentGroupAggregateRow(
                        LearningAssessmentGroupAggregateRow.GroupDimension.valueOf(
                                rs.getString("dimension")),
                        rs.getString("group_code"),
                        rs.getInt("item_count"),
                        rs.getInt("graded_result_count"),
                        rs.getObject("accuracy_rate", BigDecimal.class)))
                .list();
    }

    /** 채점 결과가 있는 문항 중 완전정답률이 낮은 다섯 문항을 반환한다. */
    public List<LearningAssessmentPriorityItemRow> findPriorityItems(long assignmentId) {
        String sql = ITEM_RESULT_CTE + """
                SELECT worksheet_item_id,
                       item_number,
                       question_title,
                       evaluation_area,
                       difficulty,
                       COUNT(*) FILTER (WHERE is_correct) AS correct_student_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_student_count
                FROM item_result
                GROUP BY worksheet_item_id, item_number, question_title,
                         evaluation_area, difficulty
                HAVING COUNT(*) FILTER (
                    WHERE graded_unit_count = expected_unit_count
                ) > 0
                ORDER BY
                    1.0 * COUNT(*) FILTER (WHERE is_correct)
                    / COUNT(*) FILTER (
                        WHERE graded_unit_count = expected_unit_count
                    ) ASC,
                    item_number ASC
                LIMIT 5
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new LearningAssessmentPriorityItemRow(
                        rs.getLong("worksheet_item_id"),
                        rs.getInt("item_number"),
                        rs.getString("question_title"),
                        rs.getString("evaluation_area"),
                        rs.getInt("difficulty"),
                        rs.getInt("correct_student_count"),
                        rs.getInt("graded_student_count")))
                .list();
    }

    /** 학습평가에 포함된 소분류를 첫 문항 순서대로 반환한다. */
    public List<LearningSubcategoryColumnRow> findSubcategoryColumns(long assignmentId) {
        return jdbcClient.sql("""
                        SELECT pq.sub_unit_id AS subcategory_id,
                               cu.name AS subcategory_name
                        FROM worksheet_assignment wa
                        JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                        JOIN problem_question pq ON pq.id = wi.question_id
                        JOIN curriculum_unit cu ON cu.id = pq.sub_unit_id
                        WHERE wa.id = :assignmentId
                          AND pq.deleted_at IS NULL
                        GROUP BY pq.sub_unit_id, cu.name
                        ORDER BY MIN(wi.display_order), pq.sub_unit_id
                        """)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new LearningSubcategoryColumnRow(
                        rs.getLong("subcategory_id"),
                        rs.getString("subcategory_name")))
                .list();
    }

    /** 모든 배정 학생의 소분류별 정답 수와 채점 완료 문항 수를 반환한다. */
    public List<LearningStudentSubcategoryRow> findStudentSubcategoryResults(
            long assignmentId
    ) {
        String sql = ITEM_RESULT_CTE + """
                SELECT student_id,
                       student_name,
                       sub_unit_id AS subcategory_id,
                       COUNT(*) FILTER (WHERE is_correct) AS correct_count,
                       COUNT(*) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                       ) AS graded_count,
                       MIN(item_number) AS subcategory_order
                FROM item_result
                GROUP BY student_id, student_name, sub_unit_id
                ORDER BY student_name, student_id, subcategory_order, sub_unit_id
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new LearningStudentSubcategoryRow(
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getLong("subcategory_id"),
                        rs.getInt("correct_count"),
                        rs.getInt("graded_count")))
                .list();
    }

    /** 소분류별로 채점 완료 문항을 하나 이상 틀린 학생 수를 반환한다. */
    public List<LearningSubcategoryWeaknessRow> findSubcategoryWeaknesses(
            long assignmentId
    ) {
        String sql = ITEM_RESULT_CTE + """
                SELECT sub_unit_id AS subcategory_id,
                       subcategory_name,
                       COUNT(DISTINCT student_id) FILTER (
                           WHERE graded_unit_count = expected_unit_count
                             AND NOT is_correct
                       ) AS weak_student_count,
                       MIN(item_number) AS subcategory_order
                FROM item_result
                GROUP BY sub_unit_id, subcategory_name
                ORDER BY weak_student_count DESC, subcategory_order, sub_unit_id
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new LearningSubcategoryWeaknessRow(
                        rs.getLong("subcategory_id"),
                        rs.getString("subcategory_name"),
                        rs.getInt("weak_student_count")))
                .list();
    }
}
