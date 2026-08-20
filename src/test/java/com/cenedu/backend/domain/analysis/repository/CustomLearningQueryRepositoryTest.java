package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.CustomLearningSessionRow;
import com.cenedu.backend.global.common.enums.CustomStage;
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
class CustomLearningQueryRepositoryTest {

    @Autowired
    private CustomLearningQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long sourceAssignmentId;
    private long studentId;
    private long customAssignmentId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "custom-learning-teacher", "맞춤교사");
        studentId = insertAccount("STUDENT", "custom-learning-student", "맞춤학생");
        insertStudentProfile(studentId, teacherId);
        long classId = insertClass(teacherId);
        long subUnitId = insertSubUnit();

        long sourceWorksheetId = insertWorksheet(
                teacherId, "원본 학습평가", "STANDARD", null);
        sourceAssignmentId = insertClassAssignment(sourceWorksheetId, classId);
        long sourceAssignmentStudentId = insertAssignmentStudent(
                sourceAssignmentId, studentId, "GRADED", true);
        QuestionFixture firstSource = insertQuestion(subUnitId, "source-1");
        QuestionFixture secondSource = insertQuestion(subUnitId, "source-2");
        insertWorksheetItem(sourceWorksheetId, firstSource.questionId(), 1, null);
        insertWorksheetItem(sourceWorksheetId, secondSource.questionId(), 2, null);
        insertAnswer(sourceAssignmentStudentId, firstSource.answerUnitId(), "1.00");
        insertAnswer(sourceAssignmentStudentId, secondSource.answerUnitId(), "0.00");

        long customWorksheetId = insertWorksheet(
                teacherId, "맞춤 학습 1회차", "CUSTOM", sourceAssignmentId);
        customAssignmentId = insertStudentAssignment(customWorksheetId, studentId);
        long customAssignmentStudentId = insertAssignmentStudent(
                customAssignmentId, studentId, "GRADED", true);
        QuestionFixture review = insertQuestion(subUnitId, "custom-review");
        QuestionFixture similar = insertQuestion(subUnitId, "custom-similar");
        QuestionFixture advanced = insertQuestion(subUnitId, "custom-advanced");
        insertWorksheetItem(customWorksheetId, review.questionId(), 1, "REVIEW");
        insertWorksheetItem(customWorksheetId, similar.questionId(), 2, "SIMILAR");
        insertWorksheetItem(customWorksheetId, advanced.questionId(), 3, "ADVANCED");
        insertAnswer(customAssignmentStudentId, review.answerUnitId(), "1.00");
        insertAnswer(customAssignmentStudentId, similar.answerUnitId(), "0.00");
        insertAnswer(customAssignmentStudentId, advanced.answerUnitId(), "0.00");
    }

    @Test
    @DisplayName("원본 배정 학생 여부와 연결된 맞춤 학습 회차를 조회한다")
    void findsCustomLearningSessions() {
        assertThat(repository.existsSourceAssignmentStudent(sourceAssignmentId, studentId))
                .isTrue();
        assertThat(repository.existsSourceAssignmentStudent(sourceAssignmentId, Long.MAX_VALUE))
                .isFalse();

        List<CustomLearningSessionRow> rows = repository.findSessions(
                sourceAssignmentId, studentId);

        assertThat(rows).hasSize(3);
        CustomLearningSessionRow first = rows.getFirst();
        assertThat(first.customAssignmentId()).isEqualTo(customAssignmentId);
        assertThat(first.subcategoryName()).isEqualTo("맞춤 학습 소분류");
        assertThat(first.currentDifficulty()).isEqualTo(2);
        assertThat(first.sessionCompletedItemCount()).isEqualTo(3);
        assertThat(first.sessionTotalItemCount()).isEqualTo(3);
        assertThat(first.subcategoryCompletedItemCount()).isEqualTo(3);
        assertThat(first.subcategoryTotalItemCount()).isEqualTo(3);
        assertThat(first.sourceAccuracyRate()).isEqualByComparingTo("50.0");
        assertThat(first.accuracyRate()).isEqualByComparingTo("33.3");
        assertThat(first.diagnosticCompletedItemCount()).isEqualTo(2);
        assertThat(first.diagnosticTotalItemCount()).isEqualTo(2);
        assertThat(first.diagnosticCorrectItemCount()).isEqualTo(1);
        assertThat(rows).extracting(CustomLearningSessionRow::customStage)
                .containsExactly(CustomStage.REVIEW, CustomStage.SIMILAR, CustomStage.ADVANCED);
        assertThat(rows).extracting(CustomLearningSessionRow::stageCorrectCount)
                .containsExactly(1, 0, 0);
    }

    @Test
    @DisplayName("맞춤 학습 기록이 없으면 빈 목록을 반환한다")
    void returnsEmptySessions() {
        assertThat(repository.findSessions(sourceAssignmentId, Long.MAX_VALUE)).isEmpty();
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_account(role, login_id, password_hash, name)
                        VALUES (?, ?, 'test-password', ?)
                        RETURNING id
                        """, Long.class, role, loginId, name);
    }

    private void insertStudentProfile(long targetStudentId, long teacherId) {
        jdbcTemplate.update("""
                        INSERT INTO member_student_profile(
                            user_id, registration_year, grade, owner_teacher_id
                        ) VALUES (?, 2026, 1, ?)
                        """, targetStudentId, teacherId);
    }

    private long insertClass(long teacherId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '맞춤분석반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
    }

    private long insertSubUnit() {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES (
                            'custom-learning-sub-unit', 'SUB_UNIT',
                            '맞춤 학습 소분류', 1, 997
                        )
                        RETURNING id
                        """, Long.class);
    }

    private long insertWorksheet(
            long teacherId,
            String title,
            String origin,
            Long sourceAssignment
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id,
                            grade, semester, source_assignment_id
                        ) VALUES (?, 'GENERAL_LEARNING', ?, ?, 1, '2', ?)
                        RETURNING id
                        """, Long.class, title, origin, teacherId, sourceAssignment);
    }

    private long insertClassAssignment(long worksheetId, long classId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, class_id, assigned_at, due_at
                        ) VALUES (?, ?, '2026-08-10T09:00:00+09:00',
                                  '2026-08-20T23:59:59+09:00')
                        RETURNING id
                        """, Long.class, worksheetId, classId);
    }

    private long insertStudentAssignment(long worksheetId, long targetStudentId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, student_id, assigned_at, due_at
                        ) VALUES (?, ?, '2026-08-12T09:00:00+09:00',
                                  '2026-08-22T23:59:59+09:00')
                        RETURNING id
                        """, Long.class, worksheetId, targetStudentId);
    }

    private long insertAssignmentStudent(
            long assignmentId,
            long targetStudentId,
            String status,
            boolean completed
    ) {
        OffsetDateTime gradedAt = completed
                ? OffsetDateTime.parse("2026-08-14T12:00:00+09:00")
                : null;
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, progress_count, graded_at
                        ) VALUES (?, ?, ?, 0, ?)
                        RETURNING id
                        """, Long.class, assignmentId, targetStudentId, status, gradedAt);
    }

    private QuestionFixture insertQuestion(long subUnitId, String sourceRef) {
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text
                        ) VALUES (
                            'IMPORTED', ?, ?, 2, 'SHORT_INPUT', 'TEXT_ONLY',
                            '[]'::jsonb, '맞춤 분석 문항'
                        )
                        RETURNING id
                        """, Long.class, sourceRef, subUnitId);
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, answer_raw,
                            answer_normalized, compare_method, diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '1', '1', 'EXACT', 'ANSWER')
                        RETURNING id
                        """, Long.class, questionId);
        return new QuestionFixture(questionId, answerUnitId);
    }

    private void insertWorksheetItem(
            long worksheetId,
            long questionId,
            int displayOrder,
            String customStage
    ) {
        jdbcTemplate.update("""
                        INSERT INTO worksheet_item(
                            worksheet_id, question_id, display_order, custom_stage
                        ) VALUES (?, ?, ?, ?)
                        """, worksheetId, questionId, displayOrder, customStage);
    }

    private void insertAnswer(long assignmentStudentId, long answerUnitId, String score) {
        jdbcTemplate.update("""
                        INSERT INTO submission_answer(
                            assignment_student_id, answer_unit_id, input_mode,
                            raw_latex, normalized, auto_score, final_score,
                            compare_method, grading_status
                        ) VALUES (?, ?, 'HANDWRITING', '1', '1', ?::numeric, ?::numeric,
                                  'EXACT', 'GRADED')
                        """, assignmentStudentId, answerUnitId, score, score);
    }

    private record QuestionFixture(long questionId, long answerUnitId) {
    }
}
