package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentRow;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisStudentRow;
import com.cenedu.backend.domain.analysis.repository.row.ClassAnalysisOverviewRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
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
        "app.jwt.secret=cen-edu-analysis-repository-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class AnalysisClassQueryRepositoryTest {

    @Autowired
    private AnalysisClassQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private long classId;
    private long assignmentId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "analysis-teacher", "분석교사");
        long studentOneId = insertAccount("STUDENT", "analysis-student-1", "김민수");
        long studentTwoId = insertAccount("STUDENT", "analysis-student-2", "박지수");
        insertStudentProfile(studentOneId, teacherId);
        insertStudentProfile(studentTwoId, teacherId);

        classId = jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '분석테스트반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);

        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id,
                            grade, semester, total_score
                        ) VALUES (
                            '분석 종합평가', 'COMPREHENSIVE_ASSESSMENT', 'STANDARD', ?,
                            1, '2', 100
                        )
                        RETURNING id
                        """, Long.class, teacherId);
        assignmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, class_id, assigned_at, due_at
                        ) VALUES (?, ?, now(), now() + interval '7 days')
                        RETURNING id
                        """, Long.class, worksheetId, classId);

        long assignmentStudentOneId = insertAssignmentStudent(assignmentId, studentOneId,
                "SUBMITTED");
        insertAssignmentStudent(assignmentId, studentTwoId, "NOT_STARTED");

        long subUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES ('analysis-test-sub-unit', 'SUB_UNIT', '분석 소단원', 1, 999)
                        RETURNING id
                        """, Long.class);
        List<QuestionFixture> questions = new ArrayList<>();
        for (int index = 1; index <= 3; index++) {
            questions.add(insertQuestion(subUnitId, index));
        }

        long firstItemId = insertWorksheetItem(worksheetId, questions.get(0).questionId(), 1,
                "30.00");
        long secondItemId = insertWorksheetItem(worksheetId, questions.get(1).questionId(), 2,
                "30.00");
        long thirdItemId = insertWorksheetItem(worksheetId, questions.get(2).questionId(), 3,
                "40.00");

        insertAnswer(assignmentStudentOneId, questions.get(0).answerUnitId(), "GRADED",
                "30.00");
        insertAnswer(assignmentStudentOneId, questions.get(1).answerUnitId(), "GRADED",
                "15.00");
        insertAnswer(assignmentStudentOneId, questions.get(2).answerUnitId(), "NOT_GRADED",
                null);

        insertTime(assignmentStudentOneId, firstItemId, 10);
        insertTime(assignmentStudentOneId, secondItemId, 20);
        insertTime(assignmentStudentOneId, thirdItemId, 999);
    }

    @Test
    @DisplayName("학습지 목록은 교사·반·학기 조건과 분석 가능 여부를 반환한다")
    void findsAssignments() {
        List<AnalysisAssignmentRow> rows = repository.findAssignments(
                teacherId, classId, 2, WorksheetType.COMPREHENSIVE_ASSESSMENT);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().assignmentId()).isEqualTo(assignmentId);
        assertThat(rows.getFirst().analysisAvailable()).isTrue();
    }

    @Test
    @DisplayName("종합평가 학급 요약은 채점 완료 문항의 배점으로 성취율을 계산한다")
    void aggregatesComprehensiveOverviewWithScoreRate() {
        ClassAnalysisOverviewRow row = repository.findOverview(
                assignmentId, WorksheetType.COMPREHENSIVE_ASSESSMENT);

        assertThat(row.participantCount()).isEqualTo(1);
        assertThat(row.gradingPendingStudentCount()).isEqualTo(1);
        assertThat(row.gradingPendingAnswerCount()).isEqualTo(1);
        assertThat(row.classPerformanceRate()).isEqualByComparingTo("75.0");
        assertThat(row.averageSolvingDurationMs()).isEqualTo(30000L);
        assertThat(row.weaknessStudentCount()).isZero();
    }

    @Test
    @DisplayName("학습평가 학급 요약은 완전 정답 문항 비율로 성취율을 계산한다")
    void aggregatesGeneralLearningOverviewWithAccuracyRate() {
        ClassAnalysisOverviewRow row = repository.findOverview(
                assignmentId, WorksheetType.GENERAL_LEARNING);

        assertThat(row.classPerformanceRate()).isEqualByComparingTo("50.0");
        assertThat(row.weaknessStudentCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("종합평가 학생 목록은 부분점수를 포함한 득점률을 반환한다")
    void returnsScoreRateForComprehensiveAssessmentStudents() {
        List<AnalysisStudentRow> rows = repository.findStudents(
                assignmentId, WorksheetType.COMPREHENSIVE_ASSESSMENT);

        assertThat(rows).hasSize(2);
        AnalysisStudentRow first = rows.stream()
                .filter(row -> row.studentName().equals("김민수"))
                .findFirst()
                .orElseThrow();
        AnalysisStudentRow second = rows.stream()
                .filter(row -> row.studentName().equals("박지수"))
                .findFirst()
                .orElseThrow();
        assertThat(first.gradedItemCount()).isEqualTo(2);
        assertThat(first.performanceRate()).isEqualByComparingTo("75.0");
        assertThat(second.gradedItemCount()).isZero();
        assertThat(second.performanceRate()).isNull();
    }

    @Test
    @DisplayName("학습평가 학생 목록은 완전 정답 문항 비율을 반환한다")
    void returnsAccuracyRateForGeneralLearningStudents() {
        List<AnalysisStudentRow> rows = repository.findStudents(
                assignmentId, WorksheetType.GENERAL_LEARNING);

        AnalysisStudentRow student = rows.stream()
                .filter(row -> row.studentName().equals("김민수"))
                .findFirst()
                .orElseThrow();
        assertThat(student.performanceRate()).isEqualByComparingTo("50.0");
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_account(role, login_id, password_hash, name)
                        VALUES (?, ?, 'test-password', ?)
                        RETURNING id
                        """, Long.class, role, loginId, name);
    }

    private void insertStudentProfile(long studentId, long ownerTeacherId) {
        jdbcTemplate.update("""
                        INSERT INTO member_student_profile(
                            user_id, registration_year, grade, owner_teacher_id
                        ) VALUES (?, 2026, 1, ?)
                        """, studentId, ownerTeacherId);
    }

    private long insertAssignmentStudent(long targetAssignmentId, long studentId, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, progress_count
                        ) VALUES (?, ?, ?, 0)
                        RETURNING id
                        """, Long.class, targetAssignmentId, studentId, status);
    }

    private QuestionFixture insertQuestion(long subUnitId, int number) {
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text
                        ) VALUES (
                            'IMPORTED', ?, ?, 2,
                            'MULTIPLE_CHOICE', 'TEXT_ONLY', '[]'::jsonb, ?
                        )
                        RETURNING id
                        """, Long.class, "analysis:test:" + number, subUnitId,
                "분석 테스트 문항 " + number);
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, answer_raw,
                            answer_normalized, compare_method, diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '1', '1', 'CHOICE', 'ANSWER')
                        RETURNING id
                        """, Long.class, questionId);
        return new QuestionFixture(questionId, answerUnitId);
    }

    private long insertWorksheetItem(
            long worksheetId,
            long questionId,
            int displayOrder,
            String maxScore
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_item(
                            worksheet_id, question_id, display_order, max_score
                        ) VALUES (?, ?, ?, ?::numeric)
                        RETURNING id
                        """, Long.class, worksheetId, questionId, displayOrder, maxScore);
    }

    private void insertAnswer(
            long assignmentStudentId,
            long answerUnitId,
            String gradingStatus,
            String finalScore
    ) {
        jdbcTemplate.update("""
                        INSERT INTO submission_answer(
                            assignment_student_id, answer_unit_id, input_mode,
                            raw_latex, normalized, auto_score, final_score,
                            compare_method, grading_status
                        ) VALUES (?, ?, 'CHOICE', '1', '1', ?::numeric, ?::numeric,
                                  'CHOICE', ?)
                        """, assignmentStudentId, answerUnitId, finalScore, finalScore,
                gradingStatus);
    }

    private void insertTime(long assignmentStudentId, long worksheetItemId, int seconds) {
        jdbcTemplate.update("""
                        INSERT INTO submission_question_time(
                            assignment_student_id, worksheet_item_id, time_spent_seconds
                        ) VALUES (?, ?, ?)
                        """, assignmentStudentId, worksheetItemId, seconds);
    }

    private record QuestionFixture(long questionId, long answerUnitId) {
    }
}
