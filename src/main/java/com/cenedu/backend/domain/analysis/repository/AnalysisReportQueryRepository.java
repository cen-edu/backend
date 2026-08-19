package com.cenedu.backend.domain.analysis.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.repository.row.AnalysisReportSourceRow;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 보고서 생성에 필요한 채점 상태와 대상 문항을 기존 테이블에서 읽는 조회 Repository. */
@Repository
@RequiredArgsConstructor
public class AnalysisReportQueryRepository {

    private final JdbcClient jdbcClient;

    /** 학생 수행 회차의 채점 완료 여부와 채점 결과가 마지막으로 바뀐 시각을 조회한다. */
    public Optional<AnalysisReportSourceRow> findReportSource(
            long assignmentId,
            long studentId
    ) {
        return jdbcClient.sql("""
                        SELECT was.id AS assignment_student_id,
                               was.status AS assignment_status,
                               was.graded_at,
                               (
                                   SELECT MAX(sa.overridden_at)
                                   FROM submission_answer sa
                                   WHERE sa.assignment_student_id = was.id
                               ) AS last_overridden_at
                        FROM worksheet_assignment_student was
                        WHERE was.assignment_id = :assignmentId
                          AND was.student_id = :studentId
                        """)
                .param("assignmentId", assignmentId)
                .param("studentId", studentId)
                .query((rs, rowNum) -> new AnalysisReportSourceRow(
                        rs.getLong("assignment_student_id"),
                        rs.getString("assignment_status"),
                        rs.getObject("graded_at", OffsetDateTime.class),
                        rs.getObject("last_overridden_at", OffsetDateTime.class)))
                .optional();
    }

    /**
     * 학생이 채점을 모두 마친 문항을 화면 순서대로 반환한다.
     *
     * <p>AI 문장을 만들 대상이자, LLM 응답에 엉뚱한 문항이 섞여 오지 않았는지 대조하는 기준이다.
     * 학생이 손대지 않은 문항은 채점 완료 수가 답안 칸 수에 못 미쳐 여기에 포함되지 않는다.
     */
    public List<Long> findGradedWorksheetItemIds(long assignmentId, long assignmentStudentId) {
        return jdbcClient.sql("""
                        SELECT wi.id AS worksheet_item_id
                        FROM worksheet_assignment wa
                        JOIN worksheet_item wi ON wi.worksheet_id = wa.worksheet_id
                        JOIN problem_question pq ON pq.id = wi.question_id
                        JOIN problem_answer_unit pau ON pau.question_id = pq.id
                        LEFT JOIN submission_answer sa
                          ON sa.assignment_student_id = :assignmentStudentId
                         AND sa.answer_unit_id = pau.id
                        WHERE wa.id = :assignmentId
                          AND pq.deleted_at IS NULL
                        GROUP BY wi.id, wi.display_order
                        HAVING COUNT(sa.id) FILTER (
                                   WHERE sa.grading_status = 'GRADED'
                               ) = COUNT(pau.id)
                        ORDER BY wi.display_order, wi.id
                        """)
                .param("assignmentId", assignmentId)
                .param("assignmentStudentId", assignmentStudentId)
                .query(Long.class)
                .list();
    }
}
