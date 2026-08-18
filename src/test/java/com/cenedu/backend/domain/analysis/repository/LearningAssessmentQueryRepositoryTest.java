package com.cenedu.backend.domain.analysis.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningStudentSubcategoryRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryColumnRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryWeaknessRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentLearningGroupComparisonRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentLearningSubcategoryRow;
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
        "app.jwt.secret=cen-edu-learning-repository-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@Import(PostgresTestcontainer.class)
@Transactional
class LearningAssessmentQueryRepositoryTest {

    @Autowired
    private LearningAssessmentQueryRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long assignmentId;
    private long firstStudentId;
    private long firstSubcategoryId;
    private long secondSubcategoryId;
    private long firstItemId;
    private long secondItemId;
    private long thirdItemId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "learning-teacher", "학습교사");
        firstStudentId = insertAccount("STUDENT", "learning-student-1", "김민수");
        long secondStudentId = insertAccount(
                "STUDENT", "learning-student-2", "박지수");
        long thirdStudentId = insertAccount("STUDENT", "learning-student-3", "이서준");
        insertStudentProfile(firstStudentId, teacherId);
        insertStudentProfile(secondStudentId, teacherId);
        insertStudentProfile(thirdStudentId, teacherId);

        long classId = jdbcTemplate.queryForObject("""
                        INSERT INTO member_school_class(
                            academic_year, grade, name, homeroom_teacher_id, display_order
                        ) VALUES (2026, 1, '학습분석반', ?, 0)
                        RETURNING id
                        """, Long.class, teacherId);
        long worksheetId = jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet(
                            title, type, origin, owner_teacher_id, grade, semester
                        ) VALUES (
                            '학습분석 테스트', 'GENERAL_LEARNING', 'STANDARD', ?, 1, '2'
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
                firstStudentId, "SUBMITTED");
        long secondAssignmentStudentId = insertAssignmentStudent(
                secondStudentId, "GRADED");
        insertAssignmentStudent(thirdStudentId, "NOT_STARTED");

        firstSubcategoryId = insertSubcategory("learning-sub-unit-1", "소인수분해", 991);
        secondSubcategoryId = insertSubcategory("learning-sub-unit-2", "최대공약수", 992);

        QuestionFixture first = insertQuestion(
                firstSubcategoryId, 1, 1, "UNDERSTANDING", "개념 문항");
        QuestionFixture second = insertQuestion(
                firstSubcategoryId, 2, 2, "CALCULATION", "계산 문항");
        QuestionFixture third = insertQuestion(
                secondSubcategoryId, 3, 3, "REASONING", "추론 문항");
        firstItemId = insertWorksheetItem(worksheetId, first.questionId(), 1);
        secondItemId = insertWorksheetItem(worksheetId, second.questionId(), 2);
        thirdItemId = insertWorksheetItem(worksheetId, third.questionId(), 3);

        insertAnswer(firstAssignmentStudentId, first.answerUnitId(), "GRADED", "1");
        insertAnswer(firstAssignmentStudentId, second.answerUnitId(), "GRADED", "0");
        insertAnswer(firstAssignmentStudentId, third.answerUnitId(), "NOT_GRADED", null);

        insertAnswer(secondAssignmentStudentId, first.answerUnitId(), "GRADED", "0");
        insertAnswer(secondAssignmentStudentId, second.answerUnitId(), "GRADED", "1");
        insertAnswer(secondAssignmentStudentId, third.answerUnitId(), "GRADED", "1");
    }

    @Test
    @DisplayName("평가 영역과 난이도별로 채점 완료 결과의 완전정답률을 집계한다")
    void aggregatesEvaluationAreasAndDifficultyBands() {
        List<LearningAssessmentGroupAggregateRow> rows =
                repository.findGroupAggregates(assignmentId);

        LearningAssessmentGroupAggregateRow understanding = findGroup(
                rows,
                LearningAssessmentGroupAggregateRow.GroupDimension.EVALUATION_AREA,
                "UNDERSTANDING");
        LearningAssessmentGroupAggregateRow high = findGroup(
                rows,
                LearningAssessmentGroupAggregateRow.GroupDimension.DIFFICULTY,
                "HIGH");
        assertThat(understanding.itemCount()).isEqualTo(1);
        assertThat(understanding.gradedResultCount()).isEqualTo(2);
        assertThat(understanding.accuracyRate()).isEqualByComparingTo("50.0");
        assertThat(high.itemCount()).isEqualTo(1);
        assertThat(high.gradedResultCount()).isEqualTo(1);
        assertThat(high.accuracyRate()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("우선 확인 문항은 학급 정답률이 낮은 순으로 영역과 난이도를 함께 반환한다")
    void findsPriorityItems() {
        List<LearningAssessmentPriorityItemRow> rows =
                repository.findPriorityItems(assignmentId);

        assertThat(rows).hasSize(3);
        assertThat(rows).extracting(LearningAssessmentPriorityItemRow::worksheetItemId)
                .containsExactly(firstItemId, secondItemId, thirdItemId);
        assertThat(rows.getFirst().evaluationArea()).isEqualTo("UNDERSTANDING");
        assertThat(rows.getFirst().correctStudentCount()).isEqualTo(1);
        assertThat(rows.getFirst().gradedStudentCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("학생별 소분류 정답 수와 채점 완료 수를 빈 결과까지 반환한다")
    void returnsStudentSubcategoryMatrix() {
        List<LearningSubcategoryColumnRow> columns =
                repository.findSubcategoryColumns(assignmentId);
        List<LearningStudentSubcategoryRow> rows =
                repository.findStudentSubcategoryResults(assignmentId);

        assertThat(columns).extracting(LearningSubcategoryColumnRow::subcategoryId)
                .containsExactly(firstSubcategoryId, secondSubcategoryId);
        LearningStudentSubcategoryRow first = findStudentResult(
                rows, firstStudentId, firstSubcategoryId);
        LearningStudentSubcategoryRow pending = findStudentResult(
                rows, firstStudentId, secondSubcategoryId);
        assertThat(first.correctCount()).isEqualTo(1);
        assertThat(first.gradedCount()).isEqualTo(2);
        assertThat(pending.correctCount()).isZero();
        assertThat(pending.gradedCount()).isZero();
        assertThat(rows).hasSize(6);
    }

    @Test
    @DisplayName("소분류에서 한 문항 이상 틀린 학생을 취약 인원으로 집계한다")
    void countsWeakStudentsBySubcategory() {
        List<LearningSubcategoryWeaknessRow> rows =
                repository.findSubcategoryWeaknesses(assignmentId);

        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().subcategoryId()).isEqualTo(firstSubcategoryId);
        assertThat(rows.getFirst().weakStudentCount()).isEqualTo(2);
        assertThat(rows.get(1).subcategoryId()).isEqualTo(secondSubcategoryId);
        assertThat(rows.get(1).weakStudentCount()).isZero();
    }

    @Test
    @DisplayName("배정된 학생만 분석 대상으로 확인한다")
    void checksAssignmentStudent() {
        assertThat(repository.existsAssignmentStudent(assignmentId, firstStudentId))
                .isTrue();
        assertThat(repository.existsAssignmentStudent(assignmentId, -1L)).isFalse();
    }

    @Test
    @DisplayName("학생 성취 비교는 영역·난이도별로 학생과 학급 정답률을 함께 반환한다")
    void comparesStudentAndClassByGroup() {
        List<StudentLearningGroupComparisonRow> rows =
                repository.findStudentGroupComparisons(assignmentId, firstStudentId);

        StudentLearningGroupComparisonRow understanding = findComparison(
                rows,
                StudentLearningGroupComparisonRow.GroupDimension.EVALUATION_AREA,
                "UNDERSTANDING");
        assertThat(understanding.itemCount()).isEqualTo(1);
        assertThat(understanding.studentGradedResultCount()).isEqualTo(1);
        assertThat(understanding.studentAccuracyRate()).isEqualByComparingTo("100.0");
        assertThat(understanding.classGradedResultCount()).isEqualTo(2);
        assertThat(understanding.classAccuracyRate()).isEqualByComparingTo("50.0");

        StudentLearningGroupComparisonRow mid = findComparison(
                rows,
                StudentLearningGroupComparisonRow.GroupDimension.DIFFICULTY,
                "MID");
        assertThat(mid.studentAccuracyRate()).isEqualByComparingTo("0.0");
        assertThat(mid.classAccuracyRate()).isEqualByComparingTo("50.0");
    }

    @Test
    @DisplayName("학생이 채점되지 않은 영역은 학생 정답률만 null로 반환한다")
    void returnsNullStudentRateWhenNotGraded() {
        List<StudentLearningGroupComparisonRow> rows =
                repository.findStudentGroupComparisons(assignmentId, firstStudentId);

        StudentLearningGroupComparisonRow reasoning = findComparison(
                rows,
                StudentLearningGroupComparisonRow.GroupDimension.EVALUATION_AREA,
                "REASONING");
        assertThat(reasoning.studentGradedResultCount()).isZero();
        assertThat(reasoning.studentAccuracyRate()).isNull();
        assertThat(reasoning.classGradedResultCount()).isEqualTo(1);
        assertThat(reasoning.classAccuracyRate()).isEqualByComparingTo("100.0");
    }

    @Test
    @DisplayName("소분류 결과는 선택 학생만 문항 순서대로 반환한다")
    void returnsSubcategoryResultsOfSelectedStudent() {
        List<StudentLearningSubcategoryRow> rows =
                repository.findStudentSubcategoryDetails(assignmentId, firstStudentId);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(StudentLearningSubcategoryRow::subcategoryId)
                .containsExactly(firstSubcategoryId, secondSubcategoryId);
        assertThat(rows.getFirst().subcategoryName()).isEqualTo("소인수분해");
        assertThat(rows.getFirst().correctCount()).isEqualTo(1);
        assertThat(rows.getFirst().gradedCount()).isEqualTo(2);
        assertThat(rows.get(1).correctCount()).isZero();
        assertThat(rows.get(1).gradedCount()).isZero();
    }

    private StudentLearningGroupComparisonRow findComparison(
            List<StudentLearningGroupComparisonRow> rows,
            StudentLearningGroupComparisonRow.GroupDimension dimension,
            String code
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension && row.groupCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private LearningAssessmentGroupAggregateRow findGroup(
            List<LearningAssessmentGroupAggregateRow> rows,
            LearningAssessmentGroupAggregateRow.GroupDimension dimension,
            String code
    ) {
        return rows.stream()
                .filter(row -> row.dimension() == dimension && row.groupCode().equals(code))
                .findFirst()
                .orElseThrow();
    }

    private LearningStudentSubcategoryRow findStudentResult(
            List<LearningStudentSubcategoryRow> rows,
            long studentId,
            long subcategoryId
    ) {
        return rows.stream()
                .filter(row -> row.studentId() == studentId
                        && row.subcategoryId() == subcategoryId)
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

    private long insertAssignmentStudent(long studentId, String status) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_assignment_student(
                            assignment_id, student_id, status, progress_count
                        ) VALUES (?, ?, ?, 0)
                        RETURNING id
                        """, Long.class, assignmentId, studentId, status);
    }

    private long insertSubcategory(String externalKey, String name, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO curriculum_unit(
                            external_key, unit_level, name, grade, display_order
                        ) VALUES (?, 'SUB_UNIT', ?, 1, ?)
                        RETURNING id
                        """, Long.class, externalKey, name, displayOrder);
    }

    private QuestionFixture insertQuestion(
            long subcategoryId,
            int number,
            int difficulty,
            String evaluationArea,
            String title
    ) {
        long questionId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_question(
                            source_type, source_ref, sub_unit_id, difficulty,
                            question_type, presentation, content_blocks, prompt_text,
                            evaluation_area
                        ) VALUES (
                            'IMPORTED', ?, ?, ?, 'STEP_FILL', 'TEXT_ONLY',
                            jsonb_build_array(jsonb_build_object(
                                'blockKind', 'TEXT',
                                'text', ?,
                                'displayOrder', 0
                            )), ?, ?
                        )
                        RETURNING id
                        """, Long.class, "learning:test:" + number,
                subcategoryId, difficulty, title, "검색용 " + title, evaluationArea);
        long answerUnitId = jdbcTemplate.queryForObject("""
                        INSERT INTO problem_answer_unit(
                            question_id, unit_key, display_order, answer_raw,
                            answer_normalized, compare_method, diagnostic_type
                        ) VALUES (?, 'MAIN', 0, '1', '1', 'EXACT', 'ANSWER')
                        RETURNING id
                        """, Long.class, questionId);
        return new QuestionFixture(questionId, answerUnitId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                        INSERT INTO worksheet_item(
                            worksheet_id, question_id, display_order
                        ) VALUES (?, ?, ?)
                        RETURNING id
                        """, Long.class, worksheetId, questionId, displayOrder);
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
                        ) VALUES (?, ?, 'HANDWRITING', '1', '1', ?::numeric, ?::numeric,
                                  'EXACT', ?)
                        """, assignmentStudentId, answerUnitId,
                finalScore, finalScore, gradingStatus);
    }

    private record QuestionFixture(long questionId, long answerUnitId) {
    }
}
