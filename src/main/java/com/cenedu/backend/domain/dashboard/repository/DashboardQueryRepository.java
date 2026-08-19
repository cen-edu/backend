package com.cenedu.backend.domain.dashboard.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.dashboard.repository.row.DashboardAssignmentItemRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentProgressRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentStatusRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardSummaryRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardWorksheetColumnRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 반·학기 기준 대시보드 집계를 기존 학습·제출 테이블에서 읽는다. */
@Repository
@RequiredArgsConstructor
public class DashboardQueryRepository {

    private static final String RESULT_CTE = """
            WITH selected_assignment AS (
                SELECT wa.id AS assignment_id,
                       wa.assigned_at,
                       wa.due_at,
                       w.id AS worksheet_id,
                       w.title AS worksheet_title,
                       w.type AS worksheet_type,
                       w.origin AS worksheet_origin
                FROM worksheet_assignment wa
                JOIN worksheet w ON w.id = wa.worksheet_id
                WHERE wa.class_id = :classId
                  AND w.deleted_at IS NULL
                  AND (w.semester = :semester OR w.semester = 'COMMON')
            ),
            item_result AS (
                SELECT was.id AS assignment_student_id,
                       was.student_id,
                       selected.assignment_id,
                       wi.id AS worksheet_item_id,
                       COUNT(pau.id) AS expected_unit_count,
                       COUNT(answer.id) FILTER (
                           WHERE answer.grading_status = 'GRADED'
                       ) AS graded_unit_count,
                       CASE
                           WHEN COUNT(pau.id) > 0
                            AND COUNT(answer.id) FILTER (
                                WHERE answer.grading_status = 'GRADED'
                            ) = COUNT(pau.id)
                            AND CASE
                                WHEN wi.max_score IS NOT NULL
                                    THEN COALESCE(SUM(answer.final_score) FILTER (
                                        WHERE answer.grading_status = 'GRADED'
                                    ), 0) = wi.max_score
                                ELSE BOOL_AND(
                                    COALESCE(answer.final_score, 0) = 1
                                ) FILTER (
                                    WHERE answer.grading_status = 'GRADED'
                                )
                            END
                           THEN TRUE
                           ELSE FALSE
                       END AS is_correct
                FROM selected_assignment selected
                JOIN worksheet_assignment_student was
                  ON was.assignment_id = selected.assignment_id
                JOIN worksheet_item wi ON wi.worksheet_id = selected.worksheet_id
                JOIN problem_question question ON question.id = wi.question_id
                JOIN problem_answer_unit pau ON pau.question_id = question.id
                LEFT JOIN submission_answer answer
                  ON answer.assignment_student_id = was.id
                 AND answer.answer_unit_id = pau.id
                WHERE question.deleted_at IS NULL
                GROUP BY was.id, was.student_id, selected.assignment_id,
                         wi.id, wi.max_score
            )
            """;

    private final JdbcClient jdbcClient;

    /** 반의 담임 교사 ID를 조회해 대시보드 접근 권한을 확인할 수 있게 한다. */
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

    /** 선택한 반·학기의 배정·정답률·미완료·취약 학생 누적값을 반환한다. */
    public DashboardSummaryRow findSummary(long classId, int semester) {
        String sql = RESULT_CTE + """
                , assignment_progress AS (
                    SELECT selected.assignment_id,
                           COUNT(was.id) AS student_count,
                           COUNT(was.id) FILTER (
                               WHERE was.status = 'GRADED'
                           ) AS graded_student_count
                    FROM selected_assignment selected
                    LEFT JOIN worksheet_assignment_student was
                      ON was.assignment_id = selected.assignment_id
                    GROUP BY selected.assignment_id
                ),
                student_accuracy AS (
                    SELECT ir.student_id,
                           COUNT(ir.worksheet_item_id) FILTER (
                               WHERE ir.graded_unit_count = ir.expected_unit_count
                                 AND ir.expected_unit_count > 0
                           ) AS graded_item_count,
                           COUNT(ir.worksheet_item_id) FILTER (
                               WHERE ir.is_correct
                           ) AS correct_item_count
                    FROM item_result ir
                    JOIN member_class_enrollment enrollment
                      ON enrollment.student_id = ir.student_id
                     AND enrollment.class_id = :classId
                    GROUP BY ir.student_id
                )
                SELECT (SELECT COUNT(*) FROM selected_assignment) AS assignment_count,
                       (
                           SELECT COUNT(*)
                           FROM assignment_progress
                           WHERE student_count > 0
                             AND graded_student_count < student_count
                       ) AS in_progress_assignment_count,
                       (
                           SELECT ROUND(
                               100.0 * SUM(correct_item_count)
                               / NULLIF(SUM(graded_item_count), 0), 1
                           )
                           FROM student_accuracy
                       ) AS class_accuracy_rate,
                       (
                           SELECT COUNT(*)
                           FROM student_accuracy
                           WHERE graded_item_count > 0
                       ) AS aggregated_student_count,
                       (
                           SELECT COUNT(*)
                           FROM selected_assignment selected
                           JOIN worksheet_assignment_student was
                             ON was.assignment_id = selected.assignment_id
                           WHERE was.status IN ('NOT_STARTED', 'NOT_SUBMITTED')
                       ) AS incomplete_submission_count,
                       (
                           SELECT COUNT(*)
                           FROM selected_assignment selected
                           JOIN worksheet_assignment_student was
                             ON was.assignment_id = selected.assignment_id
                           WHERE was.status IN ('NOT_STARTED', 'NOT_SUBMITTED')
                             AND selected.due_at < now()
                       ) AS overdue_submission_count,
                       (
                           SELECT COUNT(*)
                           FROM student_accuracy
                           WHERE graded_item_count > 0
                             AND 100.0 * correct_item_count / graded_item_count < 60
                       ) AS weakness_student_count
                """;
        return jdbcClient.sql(sql)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .query((rs, rowNum) -> new DashboardSummaryRow(
                        rs.getInt("assignment_count"),
                        rs.getInt("in_progress_assignment_count"),
                        rs.getObject("class_accuracy_rate", BigDecimal.class),
                        rs.getInt("aggregated_student_count"),
                        rs.getInt("incomplete_submission_count"),
                        rs.getInt("overdue_submission_count"),
                        rs.getInt("weakness_student_count")))
                .single();
    }

    /** 현재 반 학생별로 지연 여부와 누적 정답률을 반환한다. */
    public List<DashboardStudentStatusRow> findStudentStatuses(long classId, int semester) {
        String sql = RESULT_CTE + """
                , student_accuracy AS (
                    SELECT student_id,
                           COUNT(worksheet_item_id) FILTER (
                               WHERE graded_unit_count = expected_unit_count
                                 AND expected_unit_count > 0
                           ) AS graded_item_count,
                           COUNT(worksheet_item_id) FILTER (
                               WHERE is_correct
                           ) AS correct_item_count
                    FROM item_result
                    GROUP BY student_id
                )
                SELECT enrollment.student_id,
                       EXISTS (
                           SELECT 1
                           FROM selected_assignment selected
                           JOIN worksheet_assignment_student was
                             ON was.assignment_id = selected.assignment_id
                           WHERE was.student_id = enrollment.student_id
                             AND was.status IN ('NOT_STARTED', 'NOT_SUBMITTED')
                             AND selected.due_at < now()
                       ) AS delayed,
                       COALESCE(accuracy.graded_item_count, 0) AS graded_item_count,
                       ROUND(
                           100.0 * accuracy.correct_item_count
                           / NULLIF(accuracy.graded_item_count, 0), 1
                       ) AS accuracy_rate
                FROM member_class_enrollment enrollment
                LEFT JOIN student_accuracy accuracy
                  ON accuracy.student_id = enrollment.student_id
                WHERE enrollment.class_id = :classId
                ORDER BY enrollment.student_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .query((rs, rowNum) -> new DashboardStudentStatusRow(
                        rs.getLong("student_id"),
                        rs.getBoolean("delayed"),
                        rs.getInt("graded_item_count"),
                        rs.getObject("accuracy_rate", BigDecimal.class)))
                .list();
    }

    /** 학생 현황 표의 학습지 열을 오래된 배정부터 반환한다. */
    public List<DashboardWorksheetColumnRow> findWorksheetColumns(
            long classId,
            int semester
    ) {
        return jdbcClient.sql("""
                        SELECT wa.id AS assignment_id,
                               w.title AS worksheet_title,
                               w.type AS worksheet_type,
                               w.origin AS worksheet_origin,
                               w.source_assignment_id,
                               wa.assigned_at,
                               wa.due_at
                        FROM worksheet_assignment wa
                        JOIN worksheet w ON w.id = wa.worksheet_id
                        WHERE wa.class_id = :classId
                          AND w.deleted_at IS NULL
                          AND (w.semester = :semester OR w.semester = 'COMMON')
                        ORDER BY wa.assigned_at ASC, wa.id ASC
                        """)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .query((rs, rowNum) -> new DashboardWorksheetColumnRow(
                        rs.getLong("assignment_id"),
                        rs.getString("worksheet_title"),
                        WorksheetType.valueOf(rs.getString("worksheet_type")),
                        WorksheetOrigin.valueOf(rs.getString("worksheet_origin")),
                        rs.getObject("source_assignment_id", Long.class),
                        rs.getObject("assigned_at", OffsetDateTime.class),
                        rs.getObject("due_at", OffsetDateTime.class)))
                .list();
    }

    /** 현재 반의 모든 학생과 학습지별 진행·점수·정답률을 반환한다. */
    public List<DashboardStudentProgressRow> findStudentProgress(
            long classId,
            int semester
    ) {
        String sql = RESULT_CTE + """
                , latest_answer AS (
                    SELECT answer.assignment_student_id,
                           MAX(answer.created_at) AS latest_answer_at
                    FROM submission_answer answer
                    GROUP BY answer.assignment_student_id
                ),
                assignment_student_metric AS (
                    SELECT was.id AS assignment_student_id,
                           was.assignment_id,
                           was.student_id,
                           was.status,
                           was.progress_count,
                           was.submitted_at,
                           was.graded_at,
                           was.total_score,
                           COUNT(ir.worksheet_item_id) FILTER (
                               WHERE ir.graded_unit_count = ir.expected_unit_count
                                 AND ir.expected_unit_count > 0
                           ) AS graded_item_count,
                           COUNT(ir.worksheet_item_id) FILTER (
                               WHERE ir.is_correct
                           ) AS correct_item_count,
                           latest.latest_answer_at
                    FROM worksheet_assignment_student was
                    JOIN selected_assignment selected
                      ON selected.assignment_id = was.assignment_id
                    LEFT JOIN item_result ir ON ir.assignment_student_id = was.id
                    LEFT JOIN latest_answer latest
                      ON latest.assignment_student_id = was.id
                    GROUP BY was.id, was.assignment_id, was.student_id, was.status,
                             was.progress_count, was.submitted_at, was.graded_at,
                             was.total_score, latest.latest_answer_at
                ),
                class_student AS (
                    SELECT enrollment.student_id,
                           account.name AS student_name
                    FROM member_class_enrollment enrollment
                    JOIN member_account account ON account.id = enrollment.student_id
                    WHERE enrollment.class_id = :classId
                )
                SELECT student.student_id,
                       student.student_name,
                       selected.assignment_id,
                       metric.status,
                       COALESCE(metric.progress_count, 0) AS progress_count,
                       selected.due_at,
                       COALESCE(metric.graded_item_count, 0) AS graded_item_count,
                       COALESCE(metric.correct_item_count, 0) AS correct_item_count,
                       metric.total_score,
                       GREATEST(
                           metric.latest_answer_at,
                           metric.submitted_at,
                           metric.graded_at
                       ) AS latest_learning_at
                FROM class_student student
                LEFT JOIN selected_assignment selected ON TRUE
                LEFT JOIN assignment_student_metric metric
                  ON metric.assignment_id = selected.assignment_id
                 AND metric.student_id = student.student_id
                ORDER BY student.student_name ASC, student.student_id ASC,
                         selected.assigned_at ASC, selected.assignment_id ASC
                """;
        return jdbcClient.sql(sql)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .query((rs, rowNum) -> {
                    String status = rs.getString("status");
                    return new DashboardStudentProgressRow(
                            rs.getLong("student_id"),
                            rs.getString("student_name"),
                            rs.getObject("assignment_id", Long.class),
                            status == null ? null : AssignmentStatus.valueOf(status),
                            rs.getInt("progress_count"),
                            rs.getObject("due_at", OffsetDateTime.class),
                            rs.getInt("graded_item_count"),
                            rs.getInt("correct_item_count"),
                            rs.getObject("total_score", BigDecimal.class),
                            rs.getObject("latest_learning_at", OffsetDateTime.class));
                })
                .list();
    }

    /** 대시보드 학습지 목록의 전체 개수를 반환한다. */
    public long countAssignments(long classId, int semester) {
        return jdbcClient.sql("""
                        SELECT COUNT(*)
                        FROM worksheet_assignment wa
                        JOIN worksheet w ON w.id = wa.worksheet_id
                        WHERE wa.class_id = :classId
                          AND w.deleted_at IS NULL
                          AND (w.semester = :semester OR w.semester = 'COMMON')
                        """)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .query(Long.class)
                .single();
    }

    /** 대시보드 학습지 배정 목록을 최신순 페이지로 반환한다. */
    public List<DashboardAssignmentItemRow> findAssignments(
            long classId,
            int semester,
            int page,
            int size
    ) {
        return jdbcClient.sql("""
                        SELECT wa.id AS assignment_id,
                               w.title AS worksheet_title,
                               w.type AS worksheet_type,
                               w.origin AS worksheet_origin,
                               w.source_assignment_id,
                               wa.assigned_at,
                               wa.due_at,
                               COUNT(was.id) AS student_count,
                               COUNT(was.id) FILTER (
                                   WHERE was.status IN ('SUBMITTED', 'GRADED')
                               ) AS submitted_student_count,
                               COUNT(was.id) FILTER (
                                   WHERE was.status = 'GRADED'
                               ) AS graded_student_count,
                               -- 확정은 제출자에게만 찍힌다. 미제출자는 공개할 결과가 없어 비어 있다.
                               COUNT(was.id) FILTER (
                                   WHERE was.released_at IS NOT NULL
                               ) AS released_student_count
                        FROM worksheet_assignment wa
                        JOIN worksheet w ON w.id = wa.worksheet_id
                        LEFT JOIN worksheet_assignment_student was
                          ON was.assignment_id = wa.id
                        WHERE wa.class_id = :classId
                          AND w.deleted_at IS NULL
                          AND (w.semester = :semester OR w.semester = 'COMMON')
                        GROUP BY wa.id, w.title, w.type, w.origin, w.source_assignment_id,
                                 wa.assigned_at, wa.due_at
                        ORDER BY wa.assigned_at DESC, wa.id DESC
                        LIMIT :size OFFSET :offset
                        """)
                .param("classId", classId)
                .param("semester", Integer.toString(semester))
                .param("size", size)
                .param("offset", page * size)
                .query((rs, rowNum) -> new DashboardAssignmentItemRow(
                        rs.getLong("assignment_id"),
                        rs.getString("worksheet_title"),
                        WorksheetType.valueOf(rs.getString("worksheet_type")),
                        WorksheetOrigin.valueOf(rs.getString("worksheet_origin")),
                        rs.getObject("source_assignment_id", Long.class),
                        rs.getObject("assigned_at", OffsetDateTime.class),
                        rs.getObject("due_at", OffsetDateTime.class),
                        rs.getInt("student_count"),
                        rs.getInt("submitted_student_count"),
                        rs.getInt("graded_student_count"),
                        rs.getInt("released_student_count")))
                .list();
    }
}
