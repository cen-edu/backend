package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.CustomLearningSessionRow;
import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 원본 배정과 연결된 학생별 맞춤 학습 수행 결과를 읽는 조회 Repository. */
@Repository
@RequiredArgsConstructor
public class CustomLearningQueryRepository {

    private final JdbcClient jdbcClient;

    /** 원본 학습지를 해당 학생이 실제로 배정받았는지 확인한다. */
    public boolean existsSourceAssignmentStudent(long assignmentId, long studentId) {
        return jdbcClient.sql("""
                        SELECT EXISTS (
                            SELECT 1
                            FROM worksheet_assignment_student
                            WHERE assignment_id = :assignmentId
                              AND student_id = :studentId
                        )
                        """)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query(Boolean.class)
                .single();
    }

    /** 원본 배정에서 파생된 맞춤 회차를 소분류와 단계별 완전정답 결과로 조회한다. */
    public List<CustomLearningSessionRow> findSessions(
            long sourceAssignmentId,
            long studentId
    ) {
        return jdbcClient.sql("""
                        WITH root_worksheet AS (
                            SELECT worksheet_id
                            FROM worksheet_assignment
                            WHERE id = :sourceAssignmentId
                        ),
                        custom_session AS (
                            SELECT custom_assignment.id AS custom_assignment_id,
                                   custom_assignment.worksheet_id,
                                   custom_worksheet.parent_worksheet_id,
                                   custom_assignment.assigned_at,
                                   custom_student.id AS assignment_student_id,
                                   custom_student.graded_at AS completed_at
                            FROM worksheet custom_worksheet
                            JOIN worksheet_assignment custom_assignment
                              ON custom_assignment.worksheet_id = custom_worksheet.id
                            JOIN worksheet_assignment_student custom_student
                              ON custom_student.assignment_id = custom_assignment.id
                             AND custom_student.student_id = :studentId
                            WHERE custom_worksheet.source_assignment_id = :sourceAssignmentId
                              AND custom_worksheet.origin = 'CUSTOM'
                              AND custom_worksheet.deleted_at IS NULL
                              AND custom_assignment.student_id = :studentId
                        ),
                        custom_item_result AS (
                            SELECT session.custom_assignment_id,
                                   item.id AS worksheet_item_id,
                                   item.custom_stage,
                                   question.sub_unit_id,
                                   question.difficulty,
                                   COUNT(answer_unit.id) AS expected_unit_count,
                                   COUNT(answer.id) FILTER (
                                       WHERE answer.grading_status = 'GRADED'
                                   ) AS graded_unit_count,
                                   CASE
                                       WHEN COUNT(answer_unit.id) > 0
                                        AND COUNT(answer.id) FILTER (
                                            WHERE answer.grading_status = 'GRADED'
                                        ) = COUNT(answer_unit.id)
                                        AND CASE
                                            WHEN item.max_score IS NOT NULL
                                                THEN COALESCE(SUM(answer.final_score), 0)
                                                    = item.max_score
                                            ELSE BOOL_AND(
                                                COALESCE(answer.final_score, 0) = 1
                                            )
                                        END
                                       THEN TRUE
                                       ELSE FALSE
                                   END AS is_correct
                            FROM custom_session session
                            JOIN worksheet_item item
                              ON item.worksheet_id = session.worksheet_id
                            JOIN problem_question question
                              ON question.id = item.question_id
                             AND question.deleted_at IS NULL
                            JOIN problem_answer_unit answer_unit
                              ON answer_unit.question_id = question.id
                            LEFT JOIN submission_answer answer
                              ON answer.assignment_student_id = session.assignment_student_id
                             AND answer.answer_unit_id = answer_unit.id
                            GROUP BY session.custom_assignment_id, item.id,
                                     item.custom_stage, question.sub_unit_id,
                                     question.difficulty, item.max_score
                        ),
                        source_student AS (
                            SELECT id AS assignment_student_id
                            FROM worksheet_assignment_student
                            WHERE assignment_id = :sourceAssignmentId
                              AND student_id = :studentId
                        ),
                        source_item_result AS (
                            SELECT item.id AS worksheet_item_id,
                                   question.sub_unit_id,
                                   COUNT(answer_unit.id) AS expected_unit_count,
                                   COUNT(answer.id) FILTER (
                                       WHERE answer.grading_status = 'GRADED'
                                   ) AS graded_unit_count,
                                   CASE
                                       WHEN COUNT(answer_unit.id) > 0
                                        AND COUNT(answer.id) FILTER (
                                            WHERE answer.grading_status = 'GRADED'
                                        ) = COUNT(answer_unit.id)
                                        AND CASE
                                            WHEN item.max_score IS NOT NULL
                                                THEN COALESCE(SUM(answer.final_score), 0)
                                                    = item.max_score
                                            ELSE BOOL_AND(
                                                COALESCE(answer.final_score, 0) = 1
                                            )
                                        END
                                       THEN TRUE
                                       ELSE FALSE
                                   END AS is_correct
                            FROM source_student student
                            JOIN worksheet_assignment source_assignment
                              ON source_assignment.id = :sourceAssignmentId
                            JOIN worksheet_item item
                              ON item.worksheet_id = source_assignment.worksheet_id
                            JOIN problem_question question
                              ON question.id = item.question_id
                             AND question.deleted_at IS NULL
                            JOIN problem_answer_unit answer_unit
                              ON answer_unit.question_id = question.id
                            LEFT JOIN submission_answer answer
                              ON answer.assignment_student_id = student.assignment_student_id
                             AND answer.answer_unit_id = answer_unit.id
                            GROUP BY item.id, question.sub_unit_id, item.max_score
                        ),
                        session_subcategory AS (
                            SELECT DISTINCT custom_assignment_id, sub_unit_id
                            FROM custom_item_result
                        ),
                        source_summary AS (
                            SELECT target.custom_assignment_id,
                                   target.sub_unit_id,
                                   ROUND(
                                       100.0 * COUNT(source.worksheet_item_id) FILTER (
                                           WHERE source.is_correct
                                       )
                                       / NULLIF(COUNT(source.worksheet_item_id) FILTER (
                                           WHERE source.graded_unit_count
                                               = source.expected_unit_count
                                       ), 0),
                                       1
                                   ) AS source_accuracy_rate
                            FROM session_subcategory target
                            LEFT JOIN source_item_result source
                              ON source.sub_unit_id = target.sub_unit_id
                            GROUP BY target.custom_assignment_id, target.sub_unit_id
                        ),
                        session_summary AS (
                            SELECT session.custom_assignment_id,
                                   session.worksheet_id,
                                   session.parent_worksheet_id,
                                   session.assigned_at,
                                   session.completed_at,
                                   COUNT(result.worksheet_item_id) AS total_item_count,
                                   COUNT(result.worksheet_item_id) FILTER (
                                       WHERE result.graded_unit_count
                                           = result.expected_unit_count
                                   ) AS completed_item_count,
                                   ROUND(
                                       100.0 * COUNT(result.worksheet_item_id) FILTER (
                                           WHERE result.is_correct
                                       )
                                       / NULLIF(COUNT(result.worksheet_item_id) FILTER (
                                           WHERE result.graded_unit_count
                                               = result.expected_unit_count
                                       ), 0),
                                       1
                                   ) AS accuracy_rate
                            FROM custom_session session
                            LEFT JOIN custom_item_result result
                              ON result.custom_assignment_id = session.custom_assignment_id
                            GROUP BY session.custom_assignment_id, session.worksheet_id,
                                     session.parent_worksheet_id, session.assigned_at,
                                     session.completed_at
                        ),
                        subcategory_summary AS (
                            SELECT result.custom_assignment_id,
                                   result.sub_unit_id,
                                   unit.name AS subcategory_name,
                                   -- 현재 난이도를 뜻하는 것은 유사 문항뿐이다. 동일 문항은 과거
                                   -- 난이도를 그대로 들고 오고 응용 문항은 상 고정이라, 세 단계를
                                   -- 함께 세면 난이도가 섞여 항상 NULL 이 된다.
                                   CASE
                                       WHEN COUNT(DISTINCT result.difficulty) FILTER (
                                           WHERE result.custom_stage = 'SIMILAR'
                                       ) = 1
                                           THEN MAX(result.difficulty) FILTER (
                                               WHERE result.custom_stage = 'SIMILAR'
                                           )
                                       ELSE NULL
                                   END AS current_difficulty,
                                   COUNT(*) AS total_item_count,
                                   COUNT(*) FILTER (
                                       WHERE result.graded_unit_count
                                           = result.expected_unit_count
                                   ) AS completed_item_count,
                                   ROUND(
                                       100.0 * COUNT(*) FILTER (WHERE result.is_correct)
                                       / NULLIF(COUNT(*) FILTER (
                                           WHERE result.graded_unit_count
                                               = result.expected_unit_count
                                       ), 0),
                                       1
                                   ) AS accuracy_rate,
                                   COUNT(*) FILTER (
                                       WHERE result.custom_stage IN ('REVIEW', 'SIMILAR')
                                   ) AS diagnostic_total_item_count,
                                   COUNT(*) FILTER (
                                       WHERE result.custom_stage IN ('REVIEW', 'SIMILAR')
                                         AND result.graded_unit_count
                                             = result.expected_unit_count
                                   ) AS diagnostic_completed_item_count,
                                   COUNT(*) FILTER (
                                       WHERE result.custom_stage IN ('REVIEW', 'SIMILAR')
                                         AND result.is_correct
                                   ) AS diagnostic_correct_item_count
                            FROM custom_item_result result
                            JOIN curriculum_unit unit ON unit.id = result.sub_unit_id
                            GROUP BY result.custom_assignment_id, result.sub_unit_id, unit.name
                        ),
                        stage_summary AS (
                            SELECT custom_assignment_id,
                                   sub_unit_id,
                                   custom_stage,
                                   COUNT(*) FILTER (WHERE is_correct) AS correct_count,
                                   COUNT(*) AS total_count
                            FROM custom_item_result
                            WHERE custom_stage IS NOT NULL
                            GROUP BY custom_assignment_id, sub_unit_id, custom_stage
                        )
                        SELECT session.custom_assignment_id,
                               session.worksheet_id,
                               session.parent_worksheet_id,
                               root_worksheet.worksheet_id AS root_worksheet_id,
                               session.assigned_at,
                               session.completed_at,
                               session.completed_item_count AS session_completed_item_count,
                               session.total_item_count AS session_total_item_count,
                               session.accuracy_rate AS session_accuracy_rate,
                               subcategory.sub_unit_id AS subcategory_id,
                               subcategory.subcategory_name,
                               subcategory.current_difficulty,
                               subcategory.completed_item_count
                                   AS subcategory_completed_item_count,
                               subcategory.total_item_count
                                   AS subcategory_total_item_count,
                               source.source_accuracy_rate,
                               subcategory.accuracy_rate,
                               subcategory.diagnostic_completed_item_count,
                               subcategory.diagnostic_total_item_count,
                               subcategory.diagnostic_correct_item_count,
                               stage.custom_stage,
                               COALESCE(stage.correct_count, 0) AS stage_correct_count,
                               COALESCE(stage.total_count, 0) AS stage_total_count
                        FROM session_summary session
                        CROSS JOIN root_worksheet
                        JOIN subcategory_summary subcategory
                          ON subcategory.custom_assignment_id = session.custom_assignment_id
                        LEFT JOIN source_summary source
                          ON source.custom_assignment_id = session.custom_assignment_id
                         AND source.sub_unit_id = subcategory.sub_unit_id
                        LEFT JOIN stage_summary stage
                          ON stage.custom_assignment_id = session.custom_assignment_id
                         AND stage.sub_unit_id = subcategory.sub_unit_id
                        ORDER BY session.assigned_at,
                                 session.custom_assignment_id,
                                 subcategory.sub_unit_id ASC,
                                 CASE stage.custom_stage
                                     WHEN 'REVIEW' THEN 1
                                     WHEN 'SIMILAR' THEN 2
                                     WHEN 'ADVANCED' THEN 3
                                     ELSE 4
                                 END
                        """)
                .param("sourceAssignmentId", sourceAssignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new CustomLearningSessionRow(
                        rs.getLong("custom_assignment_id"),
                        rs.getLong("worksheet_id"),
                        rs.getObject("parent_worksheet_id", Long.class),
                        rs.getLong("root_worksheet_id"),
                        rs.getObject("assigned_at", OffsetDateTime.class),
                        rs.getObject("completed_at", OffsetDateTime.class),
                        rs.getInt("session_completed_item_count"),
                        rs.getInt("session_total_item_count"),
                        rs.getObject("session_accuracy_rate", BigDecimal.class),
                        rs.getLong("subcategory_id"),
                        rs.getString("subcategory_name"),
                        rs.getObject("current_difficulty", Integer.class),
                        rs.getInt("subcategory_completed_item_count"),
                        rs.getInt("subcategory_total_item_count"),
                        rs.getObject("source_accuracy_rate", BigDecimal.class),
                        rs.getObject("accuracy_rate", BigDecimal.class),
                        rs.getInt("diagnostic_completed_item_count"),
                        rs.getInt("diagnostic_total_item_count"),
                        rs.getInt("diagnostic_correct_item_count"),
                        customStage(rs.getString("custom_stage")),
                        rs.getInt("stage_correct_count"),
                        rs.getInt("stage_total_count")))
                .list();
    }

    private CustomStage customStage(String value) {
        return value == null ? null : CustomStage.valueOf(value);
    }
}
