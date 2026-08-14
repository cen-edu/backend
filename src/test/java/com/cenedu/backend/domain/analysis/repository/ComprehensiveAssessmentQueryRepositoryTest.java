package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.AssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentStudentItemRow;
import com.cenedu.backend.domain.analysis.repository.row.ScoreTimeStudentRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
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
        "app.jwt.secret=cen-edu-comprehensive-repository-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class ComprehensiveAssessmentQueryRepositoryTest {

    @Autowired
    private ComprehensiveAssessmentQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long assignmentId;
    private long firstItemId;
    private long secondItemId;
    private long thirdItemId;
    private long firstStudentId;
    private long thirdStudentId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "comprehensive-teacher", "종합교사");
        firstStudentId = insertAccount("STUDENT", "comprehensive-student-1", "김민수");
        long secondStudentId = insertAccount(
                "STUDENT", "comprehensive-student-2", "박지수");
        thirdStudentId = insertAccount("STUDENT", "comprehensive-student-3", "이서준");
        insertStudentProfile(firstStudentId, teacherId);
        insertStudentProfile(secondStudentId, teacherId);
        insertStudentProfile(thirdStudentId, teacherId);

        long classId = jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '종합분석반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id,
                            grade, semester, total_score
                        ) VALUES (
                            '종합분석 테스트', 'COMPREHENSIVE_ASSESSMENT', 'STANDARD', ?,
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

        long firstAssignmentStudentId = insertAssignmentStudent(
                assignmentId, firstStudentId, "SUBMITTED");
        long secondAssignmentStudentId = insertAssignmentStudent(
                assignmentId, secondStudentId, "GRADED");
        insertAssignmentStudent(assignmentId, thirdStudentId, "NOT_STARTED");

        long subUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES (
                            'comprehensive-test-sub-unit', 'SUB_UNIT',
                            '종합분석 소단원', 1, 998
                        )
                        RETURNING id
                        """, Long.class);

        QuestionFixture first = insertQuestion(
                subUnitId, 1, "MULTIPLE_CHOICE", 1, "객관식 발문");
        QuestionFixture second = insertQuestion(
                subUnitId, 2, "STEP_FILL", 2, "주관식 발문");
        QuestionFixture third = insertQuestion(
                subUnitId, 3, "ESSAY", 3, "서술형 발문");
        firstItemId = insertWorksheetItem(worksheetId, first.questionId(), 1, "30.00");
        secondItemId = insertWorksheetItem(worksheetId, second.questionId(), 2, "30.00");
        thirdItemId = insertWorksheetItem(worksheetId, third.questionId(), 3, "40.00");

        insertAnswer(firstAssignmentStudentId, first.answerUnitId(), "GRADED", "30.00");
        insertAnswer(firstAssignmentStudentId, second.answerUnitId(), "GRADED", "15.00");
        insertAnswer(firstAssignmentStudentId, third.answerUnitId(), "NOT_GRADED", null);
        insertTime(firstAssignmentStudentId, firstItemId, 10);
        insertTime(firstAssignmentStudentId, secondItemId, 20);
        insertTime(firstAssignmentStudentId, thirdItemId, 999);

        insertAnswer(secondAssignmentStudentId, first.answerUnitId(), "GRADED", "0.00");
        insertAnswer(secondAssignmentStudentId, second.answerUnitId(), "GRADED", "30.00");
        insertAnswer(secondAssignmentStudentId, third.answerUnitId(), "NOT_GRADED", null);
        insertTime(secondAssignmentStudentId, firstItemId, 40);
    }

    @Test
    @DisplayName("문항 유형과 난이도별로 채점 완료 결과의 완전정답률을 집계한다")
    void aggregatesQuestionTypesAndDifficultyBands() {
        List<AssessmentGroupAggregateRow> rows = repository.findGroupAggregates(assignmentId);

        assertThat(rows).hasSize(6);
        AssessmentGroupAggregateRow multipleChoice = findGroup(
                rows, AssessmentGroupAggregateRow.GroupDimension.QUESTION_TYPE,
                "MULTIPLE_CHOICE");
        AssessmentGroupAggregateRow shortAnswer = findGroup(
                rows, AssessmentGroupAggregateRow.GroupDimension.QUESTION_TYPE,
                "SHORT_ANSWER");
        AssessmentGroupAggregateRow high = findGroup(
                rows, AssessmentGroupAggregateRow.GroupDimension.DIFFICULTY, "HIGH");
        assertThat(multipleChoice.itemCount()).isEqualTo(1);
        assertThat(multipleChoice.accuracyRate()).isEqualByComparingTo("50.0");
        assertThat(shortAnswer.accuracyRate()).isEqualByComparingTo("50.0");
        assertThat(high.gradedResultCount()).isZero();
        assertThat(high.accuracyRate()).isNull();
    }

    @Test
    @DisplayName("우선 확인 문항은 채점 결과가 있는 문항만 낮은 정답률 순으로 반환한다")
    void findsPriorityItems() {
        List<AssessmentPriorityItemRow> rows = repository.findPriorityItems(assignmentId);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(AssessmentPriorityItemRow::worksheetItemId)
                .containsExactly(firstItemId, secondItemId);
        assertThat(rows.getFirst().questionTitle()).isEqualTo("객관식 발문");
        assertThat(rows.getFirst().correctStudentCount()).isEqualTo(1);
        assertThat(rows.getFirst().gradedStudentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("문항 성취는 부분점수와 채점 대기 문항의 측정시간을 구분해 반환한다")
    void returnsStudentItemMatrix() {
        List<AssessmentStudentItemRow> rows = repository.findStudentItemResults(assignmentId);

        assertThat(rows).hasSize(9);
        AssessmentStudentItemRow partialScore = findStudentItem(
                rows, firstStudentId, secondItemId);
        AssessmentStudentItemRow pending = findStudentItem(
                rows, firstStudentId, thirdItemId);
        AssessmentStudentItemRow notStarted = findStudentItem(
                rows, thirdStudentId, firstItemId);
        assertThat(partialScore.gradingStatus()).isEqualTo(GradingStatus.GRADED);
        assertThat(partialScore.score()).isEqualByComparingTo("15.00");
        assertThat(partialScore.solvingDurationMs()).isEqualTo(20000L);
        assertThat(pending.gradingStatus()).isEqualTo(GradingStatus.NOT_GRADED);
        assertThat(pending.score()).isNull();
        assertThat(pending.solvingDurationMs()).isEqualTo(999000L);
        assertThat(notStarted.solvingDurationMs()).isNull();
    }

    @Test
    @DisplayName("학생 총시간은 채점 완료 문항에서 측정된 시간만 합산한다")
    void aggregatesScoreAndTimeByStudent() {
        List<ScoreTimeStudentRow> rows = repository.findScoreTimeStudents(assignmentId);

        ScoreTimeStudentRow first = rows.stream()
                .filter(row -> row.studentId() == firstStudentId)
                .findFirst()
                .orElseThrow();
        ScoreTimeStudentRow third = rows.stream()
                .filter(row -> row.studentId() == thirdStudentId)
                .findFirst()
                .orElseThrow();
        assertThat(first.gradedItemCount()).isEqualTo(2);
        assertThat(first.scoreRate()).isEqualByComparingTo("75.0");
        assertThat(first.totalSolvingDurationMs()).isEqualTo(30000L);
        assertThat(third.gradedItemCount()).isZero();
        assertThat(third.scoreRate()).isNull();
        assertThat(third.totalSolvingDurationMs()).isNull();
    }

    private AssessmentGroupAggregateRow findGroup(
            List<AssessmentGroupAggregateRow> rows,
            AssessmentGroupAggregateRow.GroupDimension dimension,
            String code
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension && row.groupCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private AssessmentStudentItemRow findStudentItem(
            List<AssessmentStudentItemRow> rows,
            long studentId,
            long worksheetItemId
    ) {
        return rows.stream()
                .filter(row -> row.studentId() == studentId
                        && row.worksheetItemId() == worksheetItemId)
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

    private QuestionFixture insertQuestion(
            long subUnitId,
            int number,
            String questionType,
            int difficulty,
            String title
    ) {
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text
                        ) VALUES (
                            'IMPORTED', ?, ?, ?, ?, 'TEXT_ONLY',
                            jsonb_build_array(jsonb_build_object(
                                'blockKind', 'TEXT',
                                'text', ?,
                                'displayOrder', 0
                            )), ?
                        )
                        RETURNING id
                        """, Long.class, "comprehensive:test:" + number,
                subUnitId, difficulty, questionType, title, "검색용 " + title);
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
                        """, assignmentStudentId, answerUnitId,
                finalScore, finalScore, gradingStatus);
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
