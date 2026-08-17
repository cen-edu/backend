package com.cenedu.backend.domain.worksheet.controller;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * task_05 단계 3(채점 결과 조회) 검증. 명세 7-1절대로 실 채점 데이터가 없어 수동 삽입으로만
 * 확인한다 — Testcontainers + {@code @Transactional}이라 매 테스트 후 자동 롤백되므로
 * INSERT한 행을 별도로 기록·삭제할 필요가 없다(지침이 우려하는 공유 DB 오염과는 다른 상황).
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-student-result-controller-test-secret-32byte",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class StudentResultControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long studentId;
    private String studentToken;
    private long teacherId;
    private long subUnitId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "result-test-teacher", "테스트교사");
        studentId = insertAccount("STUDENT", "result-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();
        subUnitId = insertCurriculumUnit();
    }

    @Test
    @DisplayName("released_at이 NULL이면 409 WORKSHEET_RESULT_NOT_RELEASED — 점수 필드가 하나도 없어야 한다")
    void getResult_notReleased_returns409() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, null);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_RESULT_NOT_RELEASED"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("객관식 정답/오답 판정과 보기 텍스트 해석이 맞다 — answer_raw는 1-based 순번이다")
    void getResult_choiceItem_resolvesTextNotRawNumber() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        long wrongChoiceId = insertChoice(questionId, 0, "1");
        long correctChoiceId = insertChoice(questionId, 1, "4"); // display_order 1 → answer_raw "2"
        long answerUnitId = insertAnswerUnit(questionId, null, "MAIN", 0, "CHOICE", "2");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        long worksheetItemId = insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        // 학생이 오답(첫 번째 보기)을 골랐고 채점 완료됨
        insertGradedChoiceAnswer(assignmentStudentId, answerUnitId, wrongChoiceId, new BigDecimal("0.00"), "CHOICE");

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].result").value("wrong"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].myAnswer").value("1"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].correctAnswer").value("4"))
                // 결과 화면은 released_at 확정 이후라 explanation을 내보낸다(단계 1의 상세 조회와 반대).
                .andExpect(jsonPath("$.data.items[0].explanation").value("해설 원문"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].result").value("wrong"));
    }

    @Test
    @DisplayName("빈칸형 여러 칸 중 일부만 맞으면 문항 판정은 partial이다")
    void getResult_stepFill_mixedGrading_isPartial() throws Exception {
        long questionId = insertQuestion("STEP_FILL");
        long stepId = insertStep(questionId, 0, "풀이 과정 1");
        long unitB1 = insertAnswerUnit(questionId, stepId, "B1", 0, "VALUE", "18");
        long unitB2 = insertAnswerUnit(questionId, stepId, "B2", 1, "VALUE", "9");

        long worksheetId = insertWorksheet("GENERAL_LEARNING");
        insertWorksheetItem(worksheetId, questionId, 1, (BigDecimal) null);
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        insertGradedHandwritingAnswer(assignmentStudentId, unitB1, "18", new BigDecimal("1.00"));
        insertGradedHandwritingAnswer(assignmentStudentId, unitB2, "7", new BigDecimal("0.00"));

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].format").value("step"))
                .andExpect(jsonPath("$.data.items[0].result").value("partial"))
                .andExpect(jsonPath("$.data.items[0].score").value(1.0))
                .andExpect(jsonPath("$.data.items[0].maxScore").value(2.0))
                .andExpect(jsonPath("$.data.summary.totalCount").value(1))
                .andExpect(jsonPath("$.data.summary.partialCount").value(1))
                // 일반 학습은 배점이 없어 요약의 점수 축을 접는다. 문항 단위 점수는 위처럼 그대로다.
                .andExpect(jsonPath("$.data.summary.score").doesNotExist())
                .andExpect(jsonPath("$.data.summary.maxScore").doesNotExist());
    }

    @Test
    @DisplayName("종합평가는 summary.score가 저장된 total_score와 일치하고 최상위 점수 필드는 없다")
    void getResult_assessment_summaryMatchesStoredTotalScore() throws Exception {
        long choiceQuestionId = insertQuestion("MULTIPLE_CHOICE");
        long wrongChoiceId = insertChoice(choiceQuestionId, 0, "1");
        insertChoice(choiceQuestionId, 1, "4");
        long choiceUnitId = insertAnswerUnit(choiceQuestionId, null, "MAIN", 0, "CHOICE", "2");

        long shortQuestionId = insertQuestion("SHORT_INPUT");
        long shortUnitId = insertAnswerUnit(shortQuestionId, null, "MAIN", 0, "VALUE", "42");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, choiceQuestionId, 1, new BigDecimal("10.00"));
        insertWorksheetItem(worksheetId, shortQuestionId, 2, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        insertGradedChoiceAnswer(assignmentStudentId, choiceUnitId, wrongChoiceId, new BigDecimal("0.00"), "CHOICE");
        insertGradedHandwritingAnswer(assignmentStudentId, shortUnitId, "42", new BigDecimal("10.00"));
        // 교사 채점이 남긴 비정규화 총점. 응답의 합산값과 구조상 같아야 한다(5-2절).
        jdbcTemplate.update(
                "UPDATE worksheet_assignment_student SET total_score = 10.00 WHERE id = ?", assignmentStudentId);
        BigDecimal storedTotalScore = jdbcTemplate.queryForObject(
                "SELECT total_score FROM worksheet_assignment_student WHERE id = ?",
                BigDecimal.class, assignmentStudentId);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("submitted"))
                // 최상위 점수는 summary로 옮겼다 — 두 곳에 같은 값을 두지 않는다.
                .andExpect(jsonPath("$.data.totalScore").doesNotExist())
                .andExpect(jsonPath("$.data.maxTotalScore").doesNotExist())
                .andExpect(jsonPath("$.data.summary.totalCount").value(2))
                .andExpect(jsonPath("$.data.summary.correctCount").value(1))
                .andExpect(jsonPath("$.data.summary.partialCount").value(0))
                .andExpect(jsonPath("$.data.summary.wrongCount").value(1))
                .andExpect(jsonPath("$.data.summary.score").value(storedTotalScore.doubleValue()))
                .andExpect(jsonPath("$.data.summary.maxScore").value(20.0))
                .andExpect(jsonPath("$.data.items[0].format").value("choice"))
                .andExpect(jsonPath("$.data.items[1].format").value("short"));
    }

    @Test
    @DisplayName("채점 대기(NOT_GRADED) 칸이 하나라도 있으면 문항 판정은 pending이다")
    void getResult_notGraded_isPending() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, null, "MAIN", 0, "VALUE", "42");

        long worksheetId = insertWorksheet("GENERAL_LEARNING");
        insertWorksheetItem(worksheetId, questionId, 1, (BigDecimal) null);
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        jdbcTemplate.update("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, raw_latex, compare_method, grading_status)
                VALUES (?, ?, 'HANDWRITING', '42', 'VALUE', 'NOT_GRADED')
                """, assignmentStudentId, answerUnitId);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].result").value("pending"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].result").value("pending"));
    }

    @Test
    @DisplayName("서술형 루브릭이 만족여부와 함께 나오고 evidence는 절대 안 나간다")
    void getResult_essay_rubricWithoutEvidence() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, null, "MAIN", 0, "RUBRIC", null);
        long rubricItem1 = insertRubricItem(questionId, 0, "최대공약수를 바르게 구했다", 5);
        long rubricItem2 = insertRubricItem(questionId, 1, "개수를 바르게 계산했다", 5);

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        long submissionAnswerId = jdbcTemplate.queryForObject("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, answer_image_ref,
                    compare_method, grading_status, final_score)
                VALUES (?, ?, 'IMAGE', 'answers/x/units/y', 'RUBRIC', 'GRADED', 5.00)
                RETURNING id
                """, Long.class, assignmentStudentId, answerUnitId);
        jdbcTemplate.update("""
                INSERT INTO grading_rubric_result(student_answer_id, rubric_item_id, satisfied, evidence)
                VALUES (?, ?, true, '근거 문장 — 학생에게 절대 노출 금지')
                """, submissionAnswerId, rubricItem1);
        jdbcTemplate.update("""
                INSERT INTO grading_rubric_result(student_answer_id, rubric_item_id, satisfied, evidence)
                VALUES (?, ?, false, '근거 문장 2')
                """, submissionAnswerId, rubricItem2);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].format").value("essay"))
                .andExpect(jsonPath("$.data.items[0].rubric").isArray())
                .andExpect(jsonPath("$.data.items[0].rubric[0].satisfied").value(true))
                .andExpect(jsonPath("$.data.items[0].rubric[1].satisfied").value(false))
                .andExpect(jsonPath("$.data.items[0].rubric[0].evidence").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].myAnswer").doesNotExist());
    }

    @Test
    @DisplayName("서술형 채점 실패(행 없음)면 rubric은 빈 배열이고 문항 판정은 pending이다")
    void getResult_essayGradingFailed_emptyRubric() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, null, "MAIN", 0, "RUBRIC", null);
        insertRubricItem(questionId, 0, "채점 기준 1", 10);

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        jdbcTemplate.update("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, answer_image_ref,
                    compare_method, grading_status, failure_reason)
                VALUES (?, ?, 'IMAGE', 'answers/x/units/y', 'RUBRIC', 'FAILED', 'LLM 호출 실패')
                """, assignmentStudentId, answerUnitId);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].result").value("pending"))
                .andExpect(jsonPath("$.data.items[0].rubric").isArray())
                .andExpect(jsonPath("$.data.items[0].rubric").isEmpty());
    }

    @Test
    @DisplayName("미제출 + 반 미확정이면 200이되 정답·해설은 조립되지 않는다 — 판정만 보인다")
    void getResult_notSubmittedAndClassNotReleased_returnsMaskedResult() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        insertChoice(questionId, 0, "1");
        insertChoice(questionId, 1, "4");
        insertAnswerUnit(questionId, null, "MAIN", 0, "CHOICE", "2");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        // 같은 배포에 확정된 형제 행이 없다.
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "NOT_SUBMITTED", null);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("not-submitted"))
                .andExpect(jsonPath("$.data.items[0].result").value("empty"))
                .andExpect(jsonPath("$.data.items[0].score").value(0))
                .andExpect(jsonPath("$.data.items[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].correctAnswer").doesNotExist());
    }

    @Test
    @DisplayName("미제출이어도 형제 행에 released_at이 있으면 반 확정으로 보고 전부 공개한다")
    void getResult_notSubmittedButClassReleased_disclosesEverything() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        insertChoice(questionId, 0, "1");
        insertChoice(questionId, 1, "4");
        insertAnswerUnit(questionId, null, "MAIN", 0, "CHOICE", "2");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "NOT_SUBMITTED", null);
        // 같은 배포의 제출자 한 명이 확정됐다 — 미제출자 본인 행은 여전히 released_at이 없다.
        insertOtherStudentAssignment(assignmentId, "GRADED", "now()");

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].explanation").value("해설 원문"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].correctAnswer").value("4"));
    }

    @Test
    @DisplayName("마감이 지난 NOT_STARTED도 미제출로 파생해 200을 낸다 — DB는 아직 NOT_SUBMITTED가 아니다")
    void getResult_notStartedPastDue_treatedAsNotSubmitted() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        insertAnswerUnit(questionId, null, "MAIN", 0, "CHOICE", "2");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertOverdueAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "NOT_STARTED", null);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].explanation").doesNotExist());
    }

    @Test
    @DisplayName("마감 전 NOT_STARTED는 미제출로 보지 않아 409로 막힌다")
    void getResult_notStartedBeforeDue_returns409() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        insertAnswerUnit(questionId, null, "MAIN", 0, "CHOICE", "2");

        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "NOT_STARTED", null);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_RESULT_NOT_RELEASED"));
    }

    @Test
    @DisplayName("남의 assignmentStudentId로 결과를 조회하면 404 WORKSHEET_ASSIGNMENT_NOT_FOUND")
    void getResult_notOwned_returns404() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, 1, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "now()");

        long otherStudentId = insertAccount("STUDENT", "result-test-other", "학생2");
        String otherToken = jwtProvider.issueAccessToken(otherStudentId, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ASSIGNMENT_NOT_FOUND"));
    }

    private void insertGradedChoiceAnswer(
            long assignmentStudentId, long answerUnitId, long selectedChoiceId, BigDecimal finalScore, String compareMethod
    ) {
        jdbcTemplate.update("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, selected_choice_id,
                    compare_method, grading_status, final_score)
                VALUES (?, ?, 'CHOICE', ?, ?, 'GRADED', ?)
                """, assignmentStudentId, answerUnitId, selectedChoiceId, compareMethod, finalScore);
    }

    private void insertGradedHandwritingAnswer(
            long assignmentStudentId, long answerUnitId, String rawLatex, BigDecimal finalScore
    ) {
        jdbcTemplate.update("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, raw_latex,
                    compare_method, grading_status, final_score)
                VALUES (?, ?, 'HANDWRITING', ?, 'VALUE', 'GRADED', ?)
                """, assignmentStudentId, answerUnitId, rawLatex, finalScore);
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
                VALUES (2026, 1, '테스트반', ?, 0)
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertCurriculumUnit() {
        long majorUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, 'result-test-major', 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'result-test-middle', 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'result-test-sub', 'SUB_UNIT', '소단원', 1, 0)
                RETURNING id
                """, Long.class, middleUnitId);
    }

    private long insertQuestion(String questionType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text, explanation)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', '[]'::jsonb, '검색용 원문 — 화면 표시 금지', '해설 원문')
                RETURNING id
                """, Long.class, subUnitId, questionType);
    }

    private long insertChoice(long questionId, int displayOrder, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_choice(question_id, display_order, content)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, questionId, displayOrder, content);
    }

    private long insertStep(long questionId, int displayOrder, String label) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_step(question_id, display_order, label, segments)
                VALUES (?, ?, ?, '[]'::jsonb)
                RETURNING id
                """, Long.class, questionId, displayOrder, label);
    }

    private long insertAnswerUnit(
            long questionId, Long stepId, String unitKey, int displayOrder, String compareMethod, String answerRaw
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_answer_unit(question_id, step_id, unit_key, display_order, compare_method, answer_raw)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, stepId, unitKey, displayOrder, compareMethod, answerRaw);
    }

    private long insertRubricItem(long questionId, int displayOrder, String label, int weight) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_rubric_item(question_id, display_order, label, weight)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, displayOrder, label, weight);
    }

    private long insertWorksheet(String type) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('결과 테스트 학습지', ?, 'STANDARD', ?, 1, '2', now())
                RETURNING id
                """, Long.class, type, teacherId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, int displayOrder, BigDecimal maxScore) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, max_score)
                VALUES (?, ?, ?, ?)
                RETURNING id
                """, Long.class, worksheetId, questionId, displayOrder, maxScore);
    }

    private long insertAssignment(long worksheetId, long classId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    /** 마감이 이미 지난 배포. NOT_STARTED를 미제출로 파생하는 경로를 만든다(§2.4). */
    private long insertOverdueAssignment(long worksheetId, long classId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now() - interval '14 days', now() - interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    /** releasedAtExpr가 null이면 released_at을 비워 게이트(409) 시나리오를 만든다. */
    private long insertAssignmentStudent(long assignmentId, String releasedAtExpr) {
        return insertAssignmentStudent(assignmentId, "GRADED", releasedAtExpr);
    }

    private long insertAssignmentStudent(long assignmentId, String status, String releasedAtExpr) {
        String releasedAtSql = releasedAtExpr == null ? "null" : releasedAtExpr;
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count, released_at)
                VALUES (?, ?, ?, 1, %s)
                RETURNING id
                """.formatted(releasedAtSql), Long.class, assignmentId, studentId, status);
    }

    /** 같은 배포의 형제 행. 반 확정 판정(§8.1)이 이 행을 보고 갈린다. */
    private void insertOtherStudentAssignment(long assignmentId, String status, String releasedAtExpr) {
        long otherStudentId = insertAccount("STUDENT", "result-test-sibling", "학생3");
        insertStudentProfile(otherStudentId, teacherId);
        String releasedAtSql = releasedAtExpr == null ? "null" : releasedAtExpr;
        jdbcTemplate.update("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count, released_at)
                VALUES (?, ?, ?, 1, %s)
                """.formatted(releasedAtSql), assignmentId, otherStudentId, status);
    }
}
