package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.time.Duration;

import com.cenedu.backend.domain.grading.dto.request.GradingAutoRequest;
import com.cenedu.backend.domain.grading.dto.response.GradingAutoStartResponse;
import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 자동채점 실행 검증(task_06 §11).
 *
 * <p><b>{@code @Transactional}을 쓰지 않는다.</b> 칸 하나가 {@code REQUIRES_NEW} 트랜잭션이라
 * 테스트 트랜잭션 안에서만 존재하는 픽스처를 채점기가 볼 수 없다. 대신 커밋해 두고 테스트마다
 * 자기 데이터를 새로 만든다(Testcontainers 라 다른 개발자 DB 를 건드리지 않는다).
 *
 * <p>채점은 {@link AnswerGradingService}를 직접 불러 동기로 돌린다 — 대상 산정은
 * {@link GradingExecutionService}로 따로 확인한다. {@code @Async} 배치를 기다리는 방식은
 * 완료 시점이 스레드 스케줄에 달려 있어 테스트가 간헐적으로 깨진다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
class GradingAutoGradingTest {

    @Autowired
    private AnswerGradingService answerGradingService;

    @Autowired
    private GradingExecutionService gradingExecutionService;

    @Autowired
    private GradingJobRegistry gradingJobRegistry;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private long studentId;
    private long classId;
    private long subUnitId;

    @BeforeEach
    void setUp() {
        long seq = jdbcTemplate.queryForObject("SELECT nextval('member_account_id_seq')", Long.class);
        teacherId = insertAccount("TEACHER", "auto-teacher-" + seq, "채점교사");
        studentId = insertAccount("STUDENT", "auto-student-" + seq, "학생1");
        insertStudentProfile(studentId, teacherId);
        classId = insertClass();
        subUnitId = insertCurriculumUnit(seq);
    }

    @Test
    @DisplayName("서술형은 FAILED로 남고 auto_score를 비워 둔다 — 0을 넣으면 영원히 0으로 굳는다")
    void rubricAnswer_isFailedWithNullAutoScore() {
        Fixture fixture = fixture("ESSAY", "RUBRIC", null, new BigDecimal("10.00"), "학생 서술 답안");

        AnswerGradingService.Outcome outcome =
                answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());

        assertThat(outcome).isEqualTo(AnswerGradingService.Outcome.FAILED);
        assertThat(column(fixture.answerId(), "grading_status", String.class)).isEqualTo("FAILED");
        assertThat(column(fixture.answerId(), "auto_score", BigDecimal.class)).isNull();
        assertThat(column(fixture.answerId(), "failure_reason", String.class))
                .isEqualTo("서술형 자동채점 미구현");
    }

    @Test
    @DisplayName("이미 auto_score가 있는 칸을 재채점해도 최초값이 유지된다")
    void regrading_keepsFirstAutoScore() {
        Fixture fixture = fixture("SHORT_INPUT", "VALUE", "7", new BigDecimal("10.00"), "7");
        // 최초 채점: 정답이므로 10.00
        answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());
        assertThat(column(fixture.answerId(), "auto_score", BigDecimal.class))
                .isEqualByComparingTo("10.00");

        // 사람 손으로 auto_score 를 다른 값으로 바꿔 두고 재채점한다.
        jdbcTemplate.update("UPDATE submission_answer SET auto_score = 99.99 WHERE id = ?",
                fixture.answerId());
        answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());

        assertThat(column(fixture.answerId(), "auto_score", BigDecimal.class))
                .as("최초 기록 후 불변")
                .isEqualByComparingTo("99.99");
        assertThat(column(fixture.answerId(), "final_score", BigDecimal.class))
                .as("재채점은 final_score 만 갱신한다")
                .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("FAILED였던 칸을 재채점하면 auto_score가 최초로 기록된다")
    void regradingFailedAnswer_recordsAutoScoreFirstTime() {
        Fixture fixture = fixture("SHORT_INPUT", "VALUE", "7", new BigDecimal("10.00"), "읽을 수 없는 답");
        answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());
        assertThat(column(fixture.answerId(), "grading_status", String.class)).isEqualTo("FAILED");
        assertThat(column(fixture.answerId(), "auto_score", BigDecimal.class)).isNull();

        // 학생 답을 읽을 수 있는 값으로 고친 뒤 재채점 = 최초 기록이다.
        jdbcTemplate.update("UPDATE submission_answer SET raw_latex = '7' WHERE id = ?",
                fixture.answerId());
        answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());

        assertThat(column(fixture.answerId(), "grading_status", String.class)).isEqualTo("GRADED");
        assertThat(column(fixture.answerId(), "auto_score", BigDecimal.class))
                .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("일반학습은 max_score가 NULL이라 만점을 1.00으로 본다")
    void practiceWorksheet_scoresOneOrZero() {
        Fixture correct = fixture("SHORT_INPUT", "VALUE", "7", null, "7");
        Fixture wrong = fixture("SHORT_INPUT", "VALUE", "7", null, "8");

        answerGradingService.gradeOne(correct.answerId(), correct.maxScore());
        answerGradingService.gradeOne(wrong.answerId(), wrong.maxScore());

        assertThat(column(correct.answerId(), "final_score", BigDecimal.class))
                .isEqualByComparingTo("1.00");
        assertThat(column(wrong.answerId(), "final_score", BigDecimal.class))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("교사가 고친 칸은 대상에서 빠지고 skippedCount에 잡히며 점수가 그대로다")
    void teacherOverriddenAnswer_isSkipped() {
        Fixture fixture = fixture("SHORT_INPUT", "VALUE", "7", new BigDecimal("10.00"), "8");
        jdbcTemplate.update("""
                UPDATE submission_answer
                   SET overridden_by = ?, overridden_at = now(), final_score = 6.00,
                       grading_status = 'GRADED'
                 WHERE id = ?
                """, teacherId, fixture.answerId());

        GradingAutoStartResponse response = start(fixture.assignmentId());

        assertThat(response.targetAnswerCount()).isZero();
        assertThat(response.skippedCount()).isEqualTo(1);
        assertThat(column(fixture.answerId(), "final_score", BigDecimal.class))
                .as("교사 작업이 자동채점에 덮이면 안 된다")
                .isEqualByComparingTo("6.00");
    }

    @Test
    @DisplayName("resetToAuto로 되돌린 칸은 다시 자동채점 대상이 된다")
    void resetToAutoAnswer_returnsToTargets() {
        Fixture fixture = fixture("SHORT_INPUT", "VALUE", "7", new BigDecimal("10.00"), "7");
        answerGradingService.gradeOne(fixture.answerId(), fixture.maxScore());
        jdbcTemplate.update("""
                UPDATE submission_answer SET overridden_by = ?, overridden_at = now(), final_score = 3.00
                 WHERE id = ?
                """, teacherId, fixture.answerId());
        assertThat(start(fixture.assignmentId()).skippedCount()).isEqualTo(1);

        jdbcTemplate.update("""
                UPDATE submission_answer SET overridden_by = null, overridden_at = null,
                       final_score = auto_score
                 WHERE id = ?
                """, fixture.answerId());

        GradingAutoStartResponse afterReset = start(fixture.assignmentId());
        assertThat(afterReset.targetAnswerCount()).isEqualTo(1);
        assertThat(afterReset.skippedCount()).isZero();
    }

    /**
     * 자동채점을 시작한다. 앞선 실행이 아직 돌고 있으면 {@code GRADING_ALREADY_RUNNING}이 나므로
     * 자리가 빌 때까지 기다린다 — 배치가 {@code @Async}라 호출이 끝나도 스레드는 남아 있다.
     */
    private GradingAutoStartResponse start(long assignmentId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (gradingJobRegistry.isRunning(assignmentId)) {
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("자동채점이 10초 안에 끝나지 않았다");
            }
            Thread.onSpinWait();
        }
        return gradingExecutionService.start(teacherId, assignmentId, new GradingAutoRequest(null));
    }

    // ===== 픽스처 =====

    private record Fixture(long assignmentId, long assignmentStudentId, long answerId,
                           BigDecimal maxScore) {
    }

    /** 문항 1개·학생 1명·답안 1칸짜리 최소 배포를 만든다. 테스트끼리 겹치지 않는다. */
    private Fixture fixture(String questionType, String compareMethod, String answerRaw,
                            BigDecimal maxScore, String studentAnswer) {
        long questionId = insertQuestion(questionType);
        long answerUnitId = insertAnswerUnit(questionId, compareMethod, answerRaw);
        long worksheetId = insertWorksheet(
                maxScore == null ? "GENERAL_LEARNING" : "COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, maxScore);
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId);
        long answerId = insertAnswer(assignmentStudentId, answerUnitId, compareMethod, studentAnswer);
        return new Fixture(assignmentId, assignmentStudentId, answerId, maxScore);
    }

    private <T> T column(long answerId, String column, Class<T> type) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM submission_answer WHERE id = ?", type, answerId);
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_account(role, login_id, password_hash, name)
                VALUES (?, ?, 'test-password-hash', ?)
                RETURNING id
                """, Long.class, role, loginId, name);
    }

    private void insertStudentProfile(long studentId, long ownerTeacherId) {
        jdbcTemplate.update("""
                INSERT INTO member_student_profile(user_id, registration_year, grade, owner_teacher_id)
                VALUES (?, 2026, 1, ?)
                """, studentId, ownerTeacherId);
    }

    private long insertClass() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_school_class(academic_year, grade, name, homeroom_teacher_id, display_order)
                VALUES (2026, 1, '자동채점반', ?, 0)
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertCurriculumUnit(long seq) {
        long majorUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, ?, 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class, "auto-major-" + seq);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, ?, 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId, "auto-middle-" + seq);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, ?, 'SUB_UNIT', '소단원', 1, 0)
                RETURNING id
                """, Long.class, middleUnitId, "auto-sub-" + seq);
    }

    private long insertQuestion(String questionType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text, explanation)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', '[]'::jsonb, '검색용 원문', '해설')
                RETURNING id
                """, Long.class, subUnitId, questionType);
    }

    private long insertAnswerUnit(long questionId, String compareMethod, String answerRaw) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_answer_unit(
                    question_id, unit_key, display_order, compare_method, answer_raw, answer_normalized)
                VALUES (?, 'MAIN', 0, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, compareMethod, answerRaw, answerRaw);
    }

    private long insertWorksheet(String type) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('자동채점 테스트', ?, 'STANDARD', ?, 1, 'COMMON', now())
                RETURNING id
                """, Long.class, type, teacherId);
    }

    private void insertWorksheetItem(long worksheetId, long questionId, BigDecimal maxScore) {
        jdbcTemplate.update("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, max_score)
                VALUES (?, ?, 1, ?)
                """, worksheetId, questionId, maxScore);
    }

    private long insertAssignment(long worksheetId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    private long insertAssignmentStudent(long assignmentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count)
                VALUES (?, ?, 'SUBMITTED', 1)
                RETURNING id
                """, Long.class, assignmentId, studentId);
    }

    private long insertAnswer(long assignmentStudentId, long answerUnitId, String compareMethod,
                              String studentAnswer) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, raw_latex,
                    compare_method, grading_status)
                VALUES (?, ?, 'HANDWRITING', ?, ?, 'NOT_GRADED')
                RETURNING id
                """, Long.class, assignmentStudentId, answerUnitId, studentAnswer, compareMethod);
    }
}
