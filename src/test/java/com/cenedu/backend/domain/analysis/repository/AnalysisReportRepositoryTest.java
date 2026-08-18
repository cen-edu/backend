package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisReportSourceRow;
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
        "app.jwt.secret=cen-edu-analysis-report-repository-test-secret-32",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class AnalysisReportRepositoryTest {

    @Autowired
    private AnalysisReportRepository reportRepository;

    @Autowired
    private AnalysisReportQueryRepository queryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long assignmentId;
    private long studentId;
    private long assignmentStudentId;
    private long gradedItemId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "report-teacher", "보고서교사");
        studentId = insertAccount("STUDENT", "report-student", "김보고");
        insertStudentProfile(studentId, teacherId);

        long classId = jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '보고서반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id, grade, semester
                        ) VALUES ('보고서 학습지', 'GENERAL_LEARNING', 'STANDARD', ?, 1, '2')
                        RETURNING id
                        """, Long.class, teacherId);
        assignmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, class_id, assigned_at, due_at
                        ) VALUES (?, ?, now(), now() + interval '7 days')
                        RETURNING id
                        """, Long.class, worksheetId, classId);
        assignmentStudentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, graded_at
                        ) VALUES (?, ?, 'GRADED', now())
                        RETURNING id
                        """, Long.class, assignmentId, studentId);

        long subUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES ('report-sub-unit', 'SUB_UNIT', '보고서 소단원', 1, 999)
                        RETURNING id
                        """, Long.class);

        // 1번 문항은 채점 완료, 2번 문항은 학생이 손대지 않아 채점 대상이 아니다.
        QuestionFixture graded = insertQuestion(subUnitId, 1);
        QuestionFixture untouched = insertQuestion(subUnitId, 2);
        gradedItemId = insertWorksheetItem(worksheetId, graded.questionId(), 1);
        insertWorksheetItem(worksheetId, untouched.questionId(), 2);
        jdbcTemplate.update("""
                        INSERT INTO submission_answer(
                            assignment_student_id, answer_unit_id, input_mode,
                            raw_latex, normalized, auto_score, final_score,
                            compare_method, grading_status
                        ) VALUES (?, ?, 'CHOICE', '1', '1', 1, 1, 'CHOICE', 'GRADED')
                        """, assignmentStudentId, graded.answerUnitId());
    }

    @Test
    @DisplayName("채점 완료 여부와 채점 결과가 바뀐 시각을 함께 조회한다")
    void findsReportSource() {
        AnalysisReportSourceRow source = queryRepository
                .findReportSource(assignmentId, studentId)
                .orElseThrow();

        assertThat(source.assignmentStudentId()).isEqualTo(assignmentStudentId);
        assertThat(source.isGraded()).isTrue();
        assertThat(source.lastGradingChangedAt()).isEqualTo(source.gradedAt());
    }

    @Test
    @DisplayName("학생이 손대지 않은 문항은 생성 대상에서 빠진다")
    void findsOnlyGradedItems() {
        List<Long> itemIds = queryRepository.findGradedWorksheetItemIds(
                assignmentId, assignmentStudentId);

        assertThat(itemIds).containsExactly(gradedItemId);
    }

    @Test
    @DisplayName("행이 없으면 새로 만들고 생성 중으로 표시한다")
    void startsGenerationForNewReport() {
        int started = reportRepository.startGeneration(
                assignmentStudentId, LocalDateTime.now(), staleCutoff());

        assertThat(started).isEqualTo(1);
        AnalysisReport report = reportRepository
                .findByAssignmentStudentId(assignmentStudentId)
                .orElseThrow();
        assertThat(report.getGenerationStatus()).isEqualTo(GenerationStatus.GENERATING);
    }

    @Test
    @DisplayName("이미 생성 중이면 두 번째 요청은 작업을 맡지 못한다")
    void blocksSecondRequestWhileGenerating() {
        reportRepository.startGeneration(
                assignmentStudentId, LocalDateTime.now(), staleCutoff());

        int second = reportRepository.startGeneration(
                assignmentStudentId, LocalDateTime.now(), staleCutoff());

        assertThat(second).isZero();
    }

    @Test
    @DisplayName("생성 중인 채로 오래 멈춘 보고서는 다시 맡는다")
    void reclaimsStuckGeneratingReport() {
        LocalDateTime longAgo = LocalDateTime.now().minusHours(1);
        reportRepository.startGeneration(assignmentStudentId, longAgo, staleCutoff());

        int reclaimed = reportRepository.startGeneration(
                assignmentStudentId, LocalDateTime.now(), staleCutoff());

        assertThat(reclaimed).isEqualTo(1);
    }

    private LocalDateTime staleCutoff() {
        return LocalDateTime.now().minusMinutes(5);
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO member_account(role, login_id, password_hash, name)
                        VALUES (?, ?, 'test-password', ?)
                        RETURNING id
                        """, Long.class, role, loginId, name);
    }

    private void insertStudentProfile(long studentId, long teacherId) {
        jdbcTemplate.update("""
                        INSERT INTO member_student_profile(
                            user_id, registration_year, grade, owner_teacher_id
                        ) VALUES (?, 2026, 1, ?)
                        """, studentId, teacherId);
    }

    private QuestionFixture insertQuestion(long subUnitId, int order) {
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text
                        ) VALUES (
                            'IMPORTED', ?, ?, 2,
                            'MULTIPLE_CHOICE', 'TEXT_ONLY', '[]'::jsonb, ?
                        )
                        RETURNING id
                        """, Long.class, "analysis:report:" + order, subUnitId,
                "보고서 문항 " + order);
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, answer_raw,
                            answer_normalized, compare_method, diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '1', '1', 'CHOICE', 'ANSWER')
                        RETURNING id
                        """, Long.class, questionId);
        return new QuestionFixture(questionId, answerUnitId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_item(worksheet_id, question_id, display_order)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """, Long.class, worksheetId, questionId, displayOrder);
    }

    private record QuestionFixture(long questionId, long answerUnitId) {
    }
}
