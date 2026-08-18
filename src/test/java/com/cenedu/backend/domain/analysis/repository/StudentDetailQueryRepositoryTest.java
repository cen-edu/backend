package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnalysisSummaryRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnswerUnitRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentItemDetailRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentWeakSubcategoryRow;
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
        "app.jwt.secret=cen-edu-student-detail-repository-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class StudentDetailQueryRepositoryTest {

    @Autowired
    private StudentDetailQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long assignmentId;
    private long firstStudentId;
    private long firstAssignmentStudentId;
    private long firstSubcategoryId;
    private long secondSubcategoryId;
    private long firstItemId;
    private long secondItemId;
    private long firstAnswerUnitId;
    private long secondAnswerUnitId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "detail-teacher", "상세교사");
        firstStudentId = insertAccount("STUDENT", "detail-student-1", "김민수");
        long secondStudentId = insertAccount("STUDENT", "detail-student-2", "박지수");
        insertStudentProfile(firstStudentId, teacherId);
        insertStudentProfile(secondStudentId, teacherId);

        long classId = jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '학생상세반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id,
                            grade, semester, total_score
                        ) VALUES (
                            '학생상세 종합평가', 'COMPREHENSIVE_ASSESSMENT',
                            'STANDARD', ?, 1, '2', 100
                        )
                        RETURNING id
                        """, Long.class, teacherId);
        assignmentId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment(
                            worksheet_id, class_id, assigned_at, due_at
                        ) VALUES (?, ?, now(), now() + interval '7 days')
                        RETURNING id
                        """, Long.class, worksheetId, classId);

        firstAssignmentStudentId = insertAssignmentStudent(firstStudentId);
        long secondAssignmentStudentId = insertAssignmentStudent(secondStudentId);
        firstSubcategoryId = insertSubcategory(
                "student-detail-sub-unit-1", "소인수분해", 981);
        secondSubcategoryId = insertSubcategory(
                "student-detail-sub-unit-2", "최대공약수", 982);

        ChoiceQuestion choiceQuestion = insertChoiceQuestion(firstSubcategoryId);
        StepQuestion stepQuestion = insertStepQuestion(secondSubcategoryId);
        firstAnswerUnitId = choiceQuestion.answerUnitId();
        secondAnswerUnitId = stepQuestion.firstAnswerUnitId();
        firstItemId = insertWorksheetItem(
                worksheetId, choiceQuestion.questionId(), 1, "40.00");
        secondItemId = insertWorksheetItem(
                worksheetId, stepQuestion.questionId(), 2, "60.00");

        insertAnswer(
                firstAssignmentStudentId, choiceQuestion.answerUnitId(),
                "CHOICE", choiceQuestion.correctChoiceId(), "1", "40.00");
        insertAnswer(
                firstAssignmentStudentId, stepQuestion.firstAnswerUnitId(),
                "HANDWRITING", null, "4", "30.00");
        insertAnswer(
                firstAssignmentStudentId, stepQuestion.secondAnswerUnitId(),
                "HANDWRITING", null, "3", "0.00");
        insertTime(firstAssignmentStudentId, firstItemId, 10);
        insertTime(firstAssignmentStudentId, secondItemId, 20);

        insertAnswer(
                secondAssignmentStudentId, choiceQuestion.answerUnitId(),
                "CHOICE", choiceQuestion.wrongChoiceId(), "2", "0.00");
        insertAnswer(
                secondAssignmentStudentId, stepQuestion.firstAnswerUnitId(),
                "HANDWRITING", null, "4", "30.00");
        insertAnswer(
                secondAssignmentStudentId, stepQuestion.secondAnswerUnitId(),
                "HANDWRITING", null, "5", "30.00");
        insertTime(secondAssignmentStudentId, firstItemId, 30);
        insertTime(secondAssignmentStudentId, secondItemId, 40);
    }

    @Test
    @DisplayName("학생 요약은 정답률·득점률과 채점 완료 문항의 총시간을 반환한다")
    void returnsStudentSummary() {
        assertThat(repository.findAssignmentStudentId(assignmentId, firstStudentId))
                .contains(firstAssignmentStudentId);

        StudentAnalysisSummaryRow row = repository.findSummary(
                assignmentId, firstStudentId);

        assertThat(row.studentName()).isEqualTo("김민수");
        assertThat(row.totalItemCount()).isEqualTo(2);
        assertThat(row.gradedItemCount()).isEqualTo(2);
        assertThat(row.correctItemCount()).isEqualTo(1);
        assertThat(row.accuracyRate()).isEqualByComparingTo("50.0");
        assertThat(row.classAccuracyRate()).isEqualByComparingTo("50.0");
        assertThat(row.scoreRate()).isEqualByComparingTo("70.0");
        assertThat(row.classScoreRate()).isEqualByComparingTo("65.0");
        assertThat(row.totalSolvingDurationMs()).isEqualTo(30000L);
        assertThat(row.classAverageSolvingDurationMs()).isEqualTo(50000L);
    }

    @Test
    @DisplayName("정답률이 60% 미만인 소분류만 취약 소분류로 반환한다")
    void returnsWeakSubcategories() {
        List<StudentWeakSubcategoryRow> rows = repository.findWeakSubcategories(
                assignmentId, firstStudentId);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().subcategoryId()).isEqualTo(secondSubcategoryId);
        assertThat(rows.getFirst().incorrectCount()).isEqualTo(1);
        assertThat(rows.getFirst().gradedCount()).isEqualTo(1);
        assertThat(rows.getFirst().accuracyRate()).isEqualByComparingTo("0.0");
        assertThat(rows).noneMatch(row -> row.subcategoryId() == firstSubcategoryId);
    }

    @Test
    @DisplayName("문항 결과는 부분점수와 문항별 학급 중앙시간을 구분한다")
    void returnsItemResults() {
        List<StudentItemDetailRow> rows = repository.findItems(
                assignmentId, firstStudentId);

        assertThat(rows).hasSize(2);
        StudentItemDetailRow correct = rows.getFirst();
        StudentItemDetailRow partial = rows.get(1);
        assertThat(correct.worksheetItemId()).isEqualTo(firstItemId);
        assertThat(correct.resultType()).isEqualTo(StudentItemResultType.CORRECT);
        assertThat(correct.classMedianSolvingDurationMs()).isEqualTo(20000L);
        assertThat(partial.worksheetItemId()).isEqualTo(secondItemId);
        assertThat(partial.resultType())
                .isEqualTo(StudentItemResultType.PARTIAL_CORRECT);
        assertThat(partial.score()).isEqualByComparingTo("30.00");
        assertThat(partial.maxScore()).isEqualByComparingTo("60.00");
        assertThat(partial.solvingDurationMs()).isEqualTo(20000L);
        assertThat(partial.classMedianSolvingDurationMs()).isEqualTo(30000L);
        assertThat(partial.correctStudentCount()).isEqualTo(1);
        assertThat(partial.gradedStudentCount()).isEqualTo(2);
        assertThat(partial.classAccuracyRate()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("객관식 보기와 단계형 답안을 화면 표시값으로 반환한다")
    void returnsAnswerUnits() {
        List<StudentAnswerUnitRow> rows = repository.findAnswerUnits(
                assignmentId, firstStudentId);

        assertThat(rows).hasSize(3);
        StudentAnswerUnitRow choice = rows.stream()
                .filter(row -> row.answerUnitId() == firstAnswerUnitId)
                .findFirst()
                .orElseThrow();
        StudentAnswerUnitRow step = rows.stream()
                .filter(row -> row.answerUnitId() == secondAnswerUnitId)
                .findFirst()
                .orElseThrow();
        assertThat(choice.studentAnswer()).isEqualTo("정답 보기");
        assertThat(choice.correctAnswer()).isEqualTo("정답 보기");
        assertThat(choice.resultType()).isEqualTo(StudentItemResultType.CORRECT);
        assertThat(step.label()).isEqualTo("계산 단계");
        assertThat(step.diagnosticType()).isEqualTo("EXECUTE");
        assertThat(step.resultType()).isEqualTo(StudentItemResultType.CORRECT);
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

    private long insertAssignmentStudent(long studentId) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, progress_count,
                            submitted_at, graded_at
                        ) VALUES (?, ?, 'GRADED', 2, now(), now())
                        RETURNING id
                        """, Long.class, assignmentId, studentId);
    }

    private long insertSubcategory(String externalKey, String name, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES (?, 'SUB_UNIT', ?, 1, ?)
                        RETURNING id
                        """, Long.class, externalKey, name, displayOrder);
    }

    private ChoiceQuestion insertChoiceQuestion(long subcategoryId) {
        long questionId = insertQuestion(
                subcategoryId, 1, "MULTIPLE_CHOICE", "UNDERSTANDING", "객관식 문항");
        long correctChoiceId = insertChoice(questionId, 0, "정답 보기");
        long wrongChoiceId = insertChoice(questionId, 1, "오답 보기");
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, label,
                            answer_raw, answer_normalized, compare_method,
                            diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '선택', '1', '1', 'CHOICE', 'INTERPRET')
                        RETURNING id
                        """, Long.class, questionId);
        return new ChoiceQuestion(
                questionId, answerUnitId, correctChoiceId, wrongChoiceId);
    }

    private StepQuestion insertStepQuestion(long subcategoryId) {
        long questionId = insertQuestion(
                subcategoryId, 2, "STEP_FILL", "CALCULATION", "단계형 문항");
        long firstUnitId = insertStepAnswerUnit(questionId, "B1", 0, "4");
        long secondUnitId = insertStepAnswerUnit(questionId, "B2", 1, "5");
        return new StepQuestion(questionId, firstUnitId, secondUnitId);
    }

    private long insertQuestion(
            long subcategoryId,
            int number,
            String questionType,
            String evaluationArea,
            String title
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text,
                            evaluation_area
                        ) VALUES (
                            'IMPORTED', ?, ?, ?, ?, 'TEXT_ONLY',
                            jsonb_build_array(jsonb_build_object(
                                'blockKind', 'TEXT',
                                'text', ?,
                                'displayOrder', 0
                            )), ?, ?
                        )
                        RETURNING id
                        """, Long.class, "student-detail:test:" + number,
                subcategoryId, number, questionType,
                title, "검색용 " + title, evaluationArea);
    }

    private long insertChoice(long questionId, int displayOrder, String content) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO problem_choice(question_id, display_order, content)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """, Long.class, questionId, displayOrder, content);
    }

    private long insertStepAnswerUnit(
            long questionId,
            String unitKey,
            int displayOrder,
            String answer
    ) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, label,
                            answer_raw, answer_normalized, compare_method,
                            diagnostic_type
                        ) VALUES (?, ?, ?, '계산 단계', ?, ?, 'EXACT', 'EXECUTE')
                        RETURNING id
                        """, Long.class, questionId, unitKey, displayOrder, answer, answer);
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
            String inputMode,
            Long selectedChoiceId,
            String rawLatex,
            String finalScore
    ) {
        jdbcTemplate.update("""
                        INSERT INTO submission_answer(
                            assignment_student_id, answer_unit_id, input_mode,
                            selected_choice_id, raw_latex, normalized,
                            auto_score, final_score, compare_method, grading_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?::numeric, ?::numeric,
                                  CASE WHEN ? = 'CHOICE' THEN 'CHOICE' ELSE 'EXACT' END,
                                  'GRADED')
                        """, assignmentStudentId, answerUnitId, inputMode,
                selectedChoiceId, rawLatex, rawLatex, finalScore, finalScore, inputMode);
    }

    private void insertTime(long assignmentStudentId, long worksheetItemId, int seconds) {
        jdbcTemplate.update("""
                        INSERT INTO submission_question_time(
                            assignment_student_id, worksheet_item_id, time_spent_seconds
                        ) VALUES (?, ?, ?)
                        """, assignmentStudentId, worksheetItemId, seconds);
    }

    private record ChoiceQuestion(
            long questionId,
            long answerUnitId,
            long correctChoiceId,
            long wrongChoiceId
    ) {
    }

    private record StepQuestion(
            long questionId,
            long firstAnswerUnitId,
            long secondAnswerUnitId
    ) {
    }
}
