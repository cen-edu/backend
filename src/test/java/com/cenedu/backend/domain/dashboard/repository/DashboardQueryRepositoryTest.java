package com.cenedu.backend.domain.dashboard.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.dashboard.repository.row.DashboardAssignmentItemRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentProgressRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentStatusRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardSummaryRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardWorksheetColumnRow;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class DashboardQueryRepositoryTest {

    @Autowired
    private DashboardQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private long classId;
    private long firstStudentId;
    private long secondStudentId;
    private long thirdStudentId;
    private long learningAssignmentId;
    private long comprehensiveAssignmentId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "dashboard-teacher", "대시교사");
        firstStudentId = insertStudent("dashboard-student-1", "김민수");
        secondStudentId = insertStudent("dashboard-student-2", "박지수");
        thirdStudentId = insertStudent("dashboard-student-3", "이서준");
        classId = insertClass();
        enroll(firstStudentId);
        enroll(secondStudentId);
        enroll(thirdStudentId);

        WorksheetFixture learning = insertWorksheet(
                "학습평가", "GENERAL_LEARNING", "STANDARD", -2, 3, false);
        learningAssignmentId = learning.assignmentId();
        WorksheetFixture comprehensive = insertWorksheet(
                "종합평가", "COMPREHENSIVE_ASSESSMENT", "STANDARD", -5, -1, true);
        comprehensiveAssignmentId = comprehensive.assignmentId();

        QuestionFixture learningQuestion = insertQuestion("dashboard:learning", 1);
        QuestionFixture comprehensiveQuestion = insertQuestion("dashboard:comprehensive", 2);
        insertWorksheetItem(learning.worksheetId(), learningQuestion.questionId(), null);
        insertWorksheetItem(comprehensive.worksheetId(), comprehensiveQuestion.questionId(), 100);

        long firstLearning = insertAssignmentStudent(
                learningAssignmentId, firstStudentId, "GRADED", 1, null);
        insertAssignmentStudent(
                learningAssignmentId, secondStudentId, "NOT_STARTED", 1, null);
        long firstComprehensive = insertAssignmentStudent(
                comprehensiveAssignmentId, firstStudentId, "GRADED", 1, "100.00");
        insertAssignmentStudent(
                comprehensiveAssignmentId, secondStudentId, "NOT_SUBMITTED", 0, null);
        insertAssignmentStudent(
                comprehensiveAssignmentId, thirdStudentId, "NOT_STARTED", 0, null);

        insertAnswer(firstLearning, learningQuestion.answerUnitId(), "1.00");
        insertAnswer(firstComprehensive, comprehensiveQuestion.answerUnitId(), "100.00");
    }

    @Test
    @DisplayName("반 소유 교사와 학기 누적 요약을 조회한다")
    void findsClassOwnerAndSummary() {
        assertThat(repository.findClassOwnerTeacherId(classId)).contains(teacherId);

        DashboardSummaryRow summary = repository.findSummary(classId, 2);

        assertThat(summary.assignmentCount()).isEqualTo(2);
        assertThat(summary.inProgressAssignmentCount()).isEqualTo(2);
        assertThat(summary.classAccuracyRate()).isEqualByComparingTo("100.0");
        assertThat(summary.aggregatedStudentCount()).isEqualTo(1);
        assertThat(summary.incompleteSubmissionCount()).isEqualTo(3);
        assertThat(summary.overdueSubmissionCount()).isEqualTo(2);
        assertThat(summary.weaknessStudentCount()).isZero();
    }

    @Test
    @DisplayName("현재 반 학생의 지연 여부와 정답률을 조회한다")
    void findsStudentStatuses() {
        List<DashboardStudentStatusRow> rows = repository.findStudentStatuses(classId, 2);

        assertThat(rows).hasSize(3);
        DashboardStudentStatusRow first = findStatus(rows, firstStudentId);
        DashboardStudentStatusRow second = findStatus(rows, secondStudentId);
        DashboardStudentStatusRow third = findStatus(rows, thirdStudentId);
        assertThat(first.delayed()).isFalse();
        assertThat(first.gradedItemCount()).isEqualTo(2);
        assertThat(first.accuracyRate()).isEqualByComparingTo("100.0");
        assertThat(second.delayed()).isTrue();
        assertThat(second.gradedItemCount()).isZero();
        assertThat(third.delayed()).isTrue();
    }

    @Test
    @DisplayName("학습지 열과 학생별 배정 칸을 빠짐없이 조회한다")
    void findsStudentProgressMatrix() {
        List<DashboardWorksheetColumnRow> columns =
                repository.findWorksheetColumns(classId, 2);
        List<DashboardStudentProgressRow> rows =
                repository.findStudentProgress(classId, 2);

        assertThat(columns).extracting(DashboardWorksheetColumnRow::assignmentId)
                .containsExactly(comprehensiveAssignmentId, learningAssignmentId);
        assertThat(rows).hasSize(6);
        DashboardStudentProgressRow completed = findProgress(
                rows, firstStudentId, learningAssignmentId);
        DashboardStudentProgressRow inProgress = findProgress(
                rows, secondStudentId, learningAssignmentId);
        DashboardStudentProgressRow notAssigned = findProgress(
                rows, thirdStudentId, learningAssignmentId);
        assertThat(completed.assignmentStatus()).isEqualTo(AssignmentStatus.GRADED);
        assertThat(completed.gradedItemCount()).isEqualTo(1);
        assertThat(completed.correctItemCount()).isEqualTo(1);
        assertThat(completed.latestLearningAt()).isNotNull();
        assertThat(inProgress.progressCount()).isEqualTo(1);
        assertThat(notAssigned.assignmentStatus()).isNull();
    }

    @Test
    @DisplayName("학습지 목록을 최신순으로 집계하고 페이지 처리한다")
    void findsAssignmentPage() {
        assertThat(repository.countAssignments(classId, 2)).isEqualTo(2);

        List<DashboardAssignmentItemRow> firstPage =
                repository.findAssignments(classId, 2, 0, 1);
        List<DashboardAssignmentItemRow> secondPage =
                repository.findAssignments(classId, 2, 1, 1);

        assertThat(firstPage).hasSize(1);
        assertThat(firstPage.getFirst().assignmentId()).isEqualTo(learningAssignmentId);
        assertThat(firstPage.getFirst().studentCount()).isEqualTo(2);
        assertThat(firstPage.getFirst().submittedStudentCount()).isEqualTo(1);
        assertThat(firstPage.getFirst().gradedStudentCount()).isEqualTo(1);
        assertThat(secondPage.getFirst().assignmentId())
                .isEqualTo(comprehensiveAssignmentId);
        assertThat(secondPage.getFirst().studentCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("반 학생에게 나간 개별 배정도 목록에 포함한다")
    void findsAssignments_includesStudentTargetedAssignment() {
        long customWorksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id, grade, semester, total_score
                        ) VALUES (?, 'GENERAL_LEARNING', 'CUSTOM', ?, 1, '2', NULL)
                        RETURNING id
                        """, Long.class, "[맞춤] 개별 배정", teacherId);
        long customAssignmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, student_id, assigned_at, due_at
                        ) VALUES (?, ?, now(), now() + interval '7 day')
                        RETURNING id
                        """, Long.class, customWorksheetId, firstStudentId);
        insertAssignmentStudent(customAssignmentId, firstStudentId, "SUBMITTED", 1, null);

        List<DashboardAssignmentItemRow> assignments =
                repository.findAssignments(classId, 2, 0, 10);

        assertThat(repository.countAssignments(classId, 2)).isEqualTo(3);
        assertThat(assignments)
                .extracting(DashboardAssignmentItemRow::assignmentId)
                .contains(customAssignmentId);
        assertThat(assignments.stream()
                .filter(row -> row.assignmentId() == customAssignmentId)
                .findFirst()
                .orElseThrow()
                .studentCount()).isEqualTo(1);
    }

    private DashboardStudentStatusRow findStatus(
            List<DashboardStudentStatusRow> rows,
            long studentId
    ) {
        return rows.stream()
                .filter(row -> row.studentId() == studentId)
                .findFirst()
                .orElseThrow();
    }

    private DashboardStudentProgressRow findProgress(
            List<DashboardStudentProgressRow> rows,
            long studentId,
            long assignmentId
    ) {
        return rows.stream()
                .filter(row -> row.studentId() == studentId
                        && row.assignmentId() == assignmentId)
                .findFirst()
                .orElseThrow();
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_account(role, login_id, password_hash, name)
                        VALUES (?, ?, 'test-password', ?)
                        RETURNING id
                        """, Long.class, role, loginId, name);
    }

    private long insertStudent(String loginId, String name) {
        long studentId = insertAccount("STUDENT", loginId, name);
        jdbcTemplate.update("""
                        INSERT INTO member_student_profile(
                            user_id, registration_year, grade, owner_teacher_id
                        ) VALUES (?, 2026, 1, ?)
                        """, studentId, teacherId);
        return studentId;
    }

    private long insertClass() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '대시보드반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
    }

    private void enroll(long studentId) {
        jdbcTemplate.update("""
                        INSERT INTO member_class_enrollment(class_id, student_id)
                        VALUES (?, ?)
                        """, classId, studentId);
    }

    private WorksheetFixture insertWorksheet(
            String title,
            String type,
            String origin,
            int assignedDayOffset,
            int dueDayOffset,
            boolean comprehensive
    ) {
        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id,
                            grade, semester, total_score
                        ) VALUES (?, ?, ?, ?, 1, '2', ?)
                        RETURNING id
                        """, Long.class, title, type, origin, teacherId,
                comprehensive ? 100 : null);
        long assignmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, class_id, assigned_at, due_at
                        ) VALUES (
                            ?, ?,
                            now() + (? * interval '1 day'),
                            now() + (? * interval '1 day')
                        )
                        RETURNING id
                        """, Long.class, worksheetId, classId,
                assignedDayOffset, dueDayOffset);
        return new WorksheetFixture(worksheetId, assignmentId);
    }

    private QuestionFixture insertQuestion(String sourceRef, int difficulty) {
        long subcategoryId = jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES (?, 'SUB_UNIT', ?, 1, ?)
                        RETURNING id
                        """, Long.class, sourceRef + ":unit", sourceRef, 900 + difficulty);
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text
                        ) VALUES (
                            'IMPORTED', ?, ?, ?, 'MULTIPLE_CHOICE',
                            'TEXT_ONLY', '[]'::jsonb, ?
                        )
                        RETURNING id
                        """, Long.class, sourceRef, subcategoryId, difficulty, sourceRef);
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, answer_raw,
                            answer_normalized, compare_method, diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '1', '1', 'CHOICE', 'ANSWER')
                        RETURNING id
                        """, Long.class, questionId);
        return new QuestionFixture(questionId, answerUnitId);
    }

    private void insertWorksheetItem(long worksheetId, long questionId, Integer maxScore) {
        jdbcTemplate.update("""
                        INSERT INTO worksheet_item(
                            worksheet_id, question_id, display_order, max_score
                        ) VALUES (?, ?, 1, ?)
                        """, worksheetId, questionId, maxScore);
    }

    private long insertAssignmentStudent(
            long assignmentId,
            long studentId,
            String status,
            int progressCount,
            String totalScore
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, progress_count,
                            submitted_at, graded_at, total_score
                        ) VALUES (
                            ?, ?, ?, ?,
                            CASE WHEN ? IN ('SUBMITTED', 'GRADED') THEN now() ELSE NULL END,
                            CASE WHEN ? = 'GRADED' THEN now() ELSE NULL END,
                            ?::numeric
                        )
                        RETURNING id
                        """, Long.class, assignmentId, studentId, status, progressCount,
                status, status, totalScore);
    }

    private void insertAnswer(long assignmentStudentId, long answerUnitId, String score) {
        jdbcTemplate.update("""
                        INSERT INTO submission_answer(
                            assignment_student_id, answer_unit_id, input_mode,
                            raw_latex, normalized, auto_score, final_score,
                            compare_method, grading_status
                        ) VALUES (?, ?, 'CHOICE', '1', '1', ?::numeric, ?::numeric,
                                  'CHOICE', 'GRADED')
                        """, assignmentStudentId, answerUnitId, score, score);
    }

    private record WorksheetFixture(long worksheetId, long assignmentId) {
    }

    private record QuestionFixture(long questionId, long answerUnitId) {
    }
}
