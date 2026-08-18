package com.cenedu.backend.domain.analysis.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentRow;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisStudentRow;
import com.cenedu.backend.domain.analysis.repository.row.ClassAnalysisOverviewRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 기존 학습·제출 테이블을 분석 화면 모양으로 읽는 analysis 소유 조회 Repository. */
@Repository
@RequiredArgsConstructor
public class AnalysisClassQueryRepository {

    private static final String ITEM_RESULT_CTE = """
            WITH item_result AS (
                SELECT was.id AS assignment_student_id,
                       wi.id AS worksheet_item_id,
                       pq.sub_unit_id,
                       COUNT(pau.id) AS expected_unit_count,
                       COUNT(sa.id) FILTER (WHERE sa.grading_status = 'GRADED')
                           AS graded_unit_count,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(sa.id) FILTER (WHERE sa.grading_status = 'GRADED')
                                = COUNT(pau.id)
                           THEN COALESCE(SUM(sa.final_score) FILTER (
                               WHERE sa.grading_status = 'GRADED'
                           ), 0)
                           ELSE NULL
                       END AS score,
                       COALESCE(wi.max_score, COUNT(pau.id)::numeric)
                           AS resolved_max_score,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(sa.id) FILTER (WHERE sa.grading_status = 'GRADED')
                                = COUNT(pau.id)
                            AND CASE
                                WHEN wi.max_score IS NOT NULL
                                    THEN COALESCE(SUM(sa.final_score), 0) = wi.max_score
                                ELSE BOOL_AND(COALESCE(sa.final_score, 0) = 1)
                            END
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct
                FROM worksheet_assignment_student was
                JOIN worksheet_assignment wa ON wa.id = was.assignment_id
                JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                JOIN problem_question pq ON pq.id = wi.question_id
                JOIN problem_answer_unit pau ON pau.question_id = pq.id
                LEFT JOIN submission_answer sa
                  ON sa.assignment_student_id = was.id
                 AND sa.answer_unit_id = pau.id
                WHERE was.assignment_id = :assignmentId
                GROUP BY was.id, wi.id, pq.sub_unit_id, wi.max_score
            )
            """;

    private final JdbcClient jdbcClient;

    /** 반의 담임 교사 ID를 조회해 목록 조회 권한을 확인할 수 있게 한다. */
    public Optional<Long> findClassOwnerTeacherId(long classId) {
        return jdbcClient.sql("""
                        SELECT homeroom_teacher_id
                        FROM member_school_class
                        WHERE id = :classId
                          AND deleted_at IS NULL
                        """)
                .param("classId", classId)
                .query(Long.class)
                .optional();
    }

    /** 교사의 반에 배정된 분석 대상 학습지를 학기와 유형 조건으로 조회한다. */
    public List<AnalysisAssignmentRow> findAssignments(
            long teacherId,
            long classId,
            int semester,
            WorksheetType worksheetType
    ) {
        String typeCondition = worksheetType == null ? "" : " AND w.type = :worksheetType\n";
        JdbcClient.StatementSpec statement = jdbcClient.sql("""
                        SELECT wa.id AS assignment_id,
                               w.title AS worksheet_title,
                               w.type AS worksheet_type,
                               EXISTS (
                                   SELECT 1
                                   FROM worksheet_assignment_student was
                                   JOIN worksheet_item wi ON wi.worksheet_id = w.id
                                   JOIN problem_answer_unit pau
                                     ON pau.question_id = wi.question_id
                                   LEFT JOIN submission_answer sa
                                     ON sa.assignment_student_id = was.id
                                    AND sa.answer_unit_id = pau.id
                                   WHERE was.assignment_id = wa.id
                                   GROUP BY was.id, wi.id
                                   HAVING COUNT(sa.id) FILTER (
                                       WHERE sa.grading_status = 'GRADED'
                                   ) = COUNT(pau.id)
                               ) AS analysis_available
                        FROM worksheet_assignment wa
                        JOIN worksheet w ON w.id = wa.worksheet_id
                        WHERE wa.class_id = :classId
                          AND w.owner_teacher_id = :teacherId
                          AND w.deleted_at IS NULL
                          AND (w.semester = :semester OR w.semester = 'COMMON')
                        """ + typeCondition + """
                        ORDER BY wa.assigned_at DESC, wa.id DESC
                        """)
                .param("teacherId", teacherId)
                .param("classId", classId)
                .param("semester", Integer.toString(semester));
        if (worksheetType != null) {
            statement = statement.param("worksheetType", worksheetType.name());
        }
        return statement.query((rs, rowNum) -> new AnalysisAssignmentRow(
                        rs.getLong("assignment_id"),
                        rs.getString("worksheet_title"),
                        WorksheetType.valueOf(rs.getString("worksheet_type")),
                        rs.getBoolean("analysis_available")))
                .list();
    }

    /** 학습지 배정의 화면 문맥과 두 교사 소유권 값을 조회한다. */
    public Optional<AnalysisAssignmentAccessRow> findAssignmentAccess(long assignmentId) {
        return jdbcClient.sql("""
                        SELECT wa.id AS assignment_id,
                               w.title AS worksheet_title,
                               w.type AS worksheet_type,
                               sc.name AS class_name,
                               w.owner_teacher_id AS worksheet_owner_teacher_id,
                               sc.homeroom_teacher_id
                        FROM worksheet_assignment wa
                        JOIN worksheet w ON w.id = wa.worksheet_id
                        JOIN member_school_class sc ON sc.id = wa.class_id
                        WHERE wa.id = :assignmentId
                          AND w.deleted_at IS NULL
                          AND sc.deleted_at IS NULL
                        """)
                .param("assignmentId", assignmentId)
                .query((rs, rowNum) -> new AnalysisAssignmentAccessRow(
                        rs.getLong("assignment_id"),
                        rs.getString("worksheet_title"),
                        WorksheetType.valueOf(rs.getString("worksheet_type")),
                        rs.getString("class_name"),
                        rs.getLong("worksheet_owner_teacher_id"),
                        rs.getLong("homeroom_teacher_id")))
                .optional();
    }

    /** 참여·채점 대기·성취율·시간·취약 학생과 소분류를 한 번에 집계한다. */
    public ClassAnalysisOverviewRow findOverview(
            long assignmentId,
            WorksheetType worksheetType
    ) {
        String sql = ITEM_RESULT_CTE + """
                , student_result AS (
                    SELECT was.id AS assignment_student_id,
                           COUNT(ir.worksheet_item_id)
                               FILTER (WHERE ir.graded_unit_count = ir.expected_unit_count)
                               AS graded_item_count,
                           COUNT(ir.worksheet_item_id)
                               FILTER (WHERE ir.is_correct) AS correct_item_count,
                           SUM(ir.score) FILTER (
                               WHERE ir.graded_unit_count = ir.expected_unit_count
                           ) AS earned_score,
                           SUM(ir.resolved_max_score) FILTER (
                               WHERE ir.graded_unit_count = ir.expected_unit_count
                           ) AS possible_score
                    FROM worksheet_assignment_student was
                    LEFT JOIN item_result ir ON ir.assignment_student_id = was.id
                    WHERE was.assignment_id = :assignmentId
                    GROUP BY was.id
                ),
                subcategory_result AS (
                    SELECT sub_unit_id,
                           COUNT(*) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                           ) AS graded_item_count,
                           COUNT(*) FILTER (WHERE is_correct) AS correct_item_count
                    FROM item_result
                    GROUP BY sub_unit_id
                ),
                student_time AS (
                    SELECT ir.assignment_student_id,
                           SUM(sqt.time_spent_seconds) AS total_seconds
                    FROM item_result ir
                    JOIN submission_question_time sqt
                      ON sqt.assignment_student_id = ir.assignment_student_id
                     AND sqt.worksheet_item_id = ir.worksheet_item_id
                    WHERE ir.graded_unit_count = ir.expected_unit_count
                    GROUP BY ir.assignment_student_id
                )
                SELECT (
                           SELECT COUNT(DISTINCT was.id)
                           FROM worksheet_assignment_student was
                           JOIN submission_answer sa ON sa.assignment_student_id = was.id
                           WHERE was.assignment_id = :assignmentId
                       ) AS participant_count,
                       (
                           SELECT COUNT(DISTINCT was.id)
                           FROM worksheet_assignment_student was
                           JOIN submission_answer sa ON sa.assignment_student_id = was.id
                           WHERE was.assignment_id = :assignmentId
                             AND sa.grading_status <> 'GRADED'
                       ) AS grading_pending_student_count,
                       (
                           SELECT COUNT(*)
                           FROM worksheet_assignment_student was
                           JOIN submission_answer sa ON sa.assignment_student_id = was.id
                           WHERE was.assignment_id = :assignmentId
                             AND sa.grading_status <> 'GRADED'
                       ) AS grading_pending_answer_count,
                       CASE
                           WHEN :usesScoreRate
                           THEN (
                               SELECT ROUND(
                                   100.0 * SUM(earned_score)
                                   / NULLIF(SUM(possible_score), 0), 1
                               )
                               FROM student_result
                           )
                           ELSE (
                               SELECT ROUND(
                                   100.0 * SUM(correct_item_count)
                                   / NULLIF(SUM(graded_item_count), 0), 1
                               )
                               FROM student_result
                           )
                       END AS class_performance_rate,
                       (
                           SELECT CAST(ROUND(AVG(total_seconds) * 1000) AS BIGINT)
                           FROM student_time
                       ) AS average_solving_duration_ms,
                       (
                           SELECT COUNT(*)
                           FROM subcategory_result
                           WHERE graded_item_count > 0
                             AND 100.0 * correct_item_count / graded_item_count < 60
                       ) AS weakness_subcategory_count,
                       (
                           SELECT COUNT(*)
                           FROM student_result
                           WHERE graded_item_count > 0
                             AND CASE
                                 WHEN :usesScoreRate
                                 THEN 100.0 * earned_score
                                      / NULLIF(possible_score, 0)
                                 ELSE 100.0 * correct_item_count
                                      / graded_item_count
                             END < 60
                       ) AS weakness_student_count
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .param("usesScoreRate",
                        worksheetType == WorksheetType.COMPREHENSIVE_ASSESSMENT)
                .query((rs, rowNum) -> new ClassAnalysisOverviewRow(
                        rs.getInt("participant_count"),
                        rs.getInt("grading_pending_student_count"),
                        rs.getInt("grading_pending_answer_count"),
                        rs.getObject("class_performance_rate", BigDecimal.class),
                        rs.getObject("average_solving_duration_ms", Long.class),
                        rs.getInt("weakness_subcategory_count"),
                        rs.getInt("weakness_student_count")))
                .single();
    }

    /** 선택한 배정의 모든 학생을 학습지 유형별 성취율과 함께 조회한다. */
    public List<AnalysisStudentRow> findStudents(
            long assignmentId,
            WorksheetType worksheetType
    ) {
        String sql = ITEM_RESULT_CTE + """
                SELECT was.student_id,
                       ma.name AS student_name,
                       COUNT(ir.worksheet_item_id)
                           FILTER (WHERE ir.graded_unit_count = ir.expected_unit_count)
                           AS graded_item_count,
                       CASE
                           WHEN :usesScoreRate
                           THEN ROUND(
                               100.0 * SUM(ir.score) FILTER (
                                   WHERE ir.graded_unit_count = ir.expected_unit_count
                               )
                               / NULLIF(SUM(ir.resolved_max_score) FILTER (
                                   WHERE ir.graded_unit_count = ir.expected_unit_count
                               ), 0), 1
                           )
                           ELSE ROUND(
                               100.0 * COUNT(ir.worksheet_item_id) FILTER (
                                   WHERE ir.is_correct
                               )
                               / NULLIF(COUNT(ir.worksheet_item_id) FILTER (
                                   WHERE ir.graded_unit_count = ir.expected_unit_count
                               ), 0), 1
                           )
                       END AS performance_rate
                FROM worksheet_assignment_student was
                JOIN member_account ma ON ma.id = was.student_id
                LEFT JOIN item_result ir ON ir.assignment_student_id = was.id
                WHERE was.assignment_id = :assignmentId
                GROUP BY was.student_id, ma.name
                ORDER BY ma.name ASC, was.student_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("assignmentId", assignmentId)
                .param("usesScoreRate",
                        worksheetType == WorksheetType.COMPREHENSIVE_ASSESSMENT)
                .query((rs, rowNum) -> new AnalysisStudentRow(
                        rs.getLong("student_id"),
                        rs.getString("student_name"),
                        rs.getInt("graded_item_count"),
                        rs.getObject("performance_rate", BigDecimal.class)))
                .list();
    }
}
