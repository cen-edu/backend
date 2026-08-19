package com.cenedu.backend.domain.submission.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** task_05 단계 2(저장·제출) 검증. 명세 6-5/8-2절의 측정 요구를 그대로 따른다. */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class StudentSubmissionControllerTest {

    private static final String FUTURE_DUE_AT = "now() + interval '7 days'";
    private static final String PAST_DUE_AT = "now() - interval '1 day'";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long studentId;
    private String studentToken;
    private long choiceQuestionId;
    private long choiceAnswerUnitId;
    private long correctChoiceId;
    private long worksheetItemId;
    private long otherWorksheetItemId;
    private long otherAnswerUnitId;

    private long buildAssignment(String dueAtExpr) {
        long teacherId = insertAccount("TEACHER", "submission-test-teacher", "테스트교사");
        studentId = insertAccount("STUDENT", "submission-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();

        long subUnitId = insertCurriculumUnit();

        choiceQuestionId = insertQuestion(subUnitId, "MULTIPLE_CHOICE");
        correctChoiceId = insertChoice(choiceQuestionId, 1, "1");
        insertChoice(choiceQuestionId, 2, "4");
        choiceAnswerUnitId = insertAnswerUnit(choiceQuestionId, "MAIN", 0, "CHOICE", String.valueOf(correctChoiceId));

        long otherQuestionId = insertQuestion(subUnitId, "SHORT_INPUT");
        otherAnswerUnitId = insertAnswerUnit(otherQuestionId, "MAIN", 0, "VALUE", "9");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING");
        worksheetItemId = insertWorksheetItem(worksheetId, choiceQuestionId, 1);
        otherWorksheetItemId = insertWorksheetItem(worksheetId, otherQuestionId, 2);

        long classId = insertClass(teacherId);
        long assignmentId = insertAssignment(worksheetId, classId, dueAtExpr);
        return insertAssignmentStudent(assignmentId, studentId);
    }

    @Test
    @DisplayName("답안을 저장하고, 같은 칸을 두 번 저장해도 행이 늘지 않는다")
    void saveAnswers_isIdempotent() throws Exception {
        long assignmentStudentId = buildAssignment(FUTURE_DUE_AT);

        String requestJson = """
                {"timeSpentSeconds": 30, "answers": [
                    {"answerUnitId": %d, "selectedChoiceId": %d, "rawLatex": null, "hasHandwriting": false}
                ]}
                """.formatted(choiceAnswerUnitId, correctChoiceId);

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doneUnits").value(1))
                .andExpect(jsonPath("$.data.totalUnits").value(2))
                .andExpect(jsonPath("$.data.status").value("in-progress"));

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.doneUnits").value(1));

        Integer rowCount = jdbcTemplate.queryForObject(
                "select count(*) from submission_answer where assignment_student_id = ?",
                Integer.class, assignmentStudentId);
        assertThat(rowCount).isEqualTo(1);

        String storedSelectedChoiceId = jdbcTemplate.queryForObject(
                "select selected_choice_id from submission_answer where assignment_student_id = ? and answer_unit_id = ?",
                String.class, assignmentStudentId, choiceAnswerUnitId);
        assertThat(storedSelectedChoiceId).isEqualTo(String.valueOf(correctChoiceId));
    }

    @Test
    @DisplayName("다른 문항의 answerUnitId로 저장하면 400 SUBMISSION_ANSWER_UNIT_MISMATCH")
    void saveAnswers_wrongAnswerUnit_returns400() throws Exception {
        long assignmentStudentId = buildAssignment(FUTURE_DUE_AT);

        String requestJson = """
                {"timeSpentSeconds": 10, "answers": [
                    {"answerUnitId": %d, "selectedChoiceId": null, "rawLatex": "9", "hasHandwriting": false}
                ]}
                """.formatted(otherAnswerUnitId);

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_ANSWER_UNIT_MISMATCH"));
    }

    @Test
    @DisplayName("그 문항의 보기가 아닌 selectedChoiceId면 400 SUBMISSION_ANSWER_UNIT_MISMATCH")
    void saveAnswers_wrongChoice_returns400() throws Exception {
        long assignmentStudentId = buildAssignment(FUTURE_DUE_AT);

        String requestJson = """
                {"timeSpentSeconds": 10, "answers": [
                    {"answerUnitId": %d, "selectedChoiceId": 999999999, "rawLatex": null, "hasHandwriting": false}
                ]}
                """.formatted(choiceAnswerUnitId);

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_ANSWER_UNIT_MISMATCH"));
    }

    @Test
    @DisplayName("제출 기한이 지났으면 저장은 409 SUBMISSION_DUE_PASSED")
    void saveAnswers_duePassed_returns409() throws Exception {
        long assignmentStudentId = buildAssignment(PAST_DUE_AT);

        String requestJson = """
                {"timeSpentSeconds": 10, "answers": [
                    {"answerUnitId": %d, "selectedChoiceId": %d, "rawLatex": null, "hasHandwriting": false}
                ]}
                """.formatted(choiceAnswerUnitId, correctChoiceId);

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_DUE_PASSED"));
    }

    @Test
    @DisplayName("남의 assignmentStudentId로 저장하면 404 WORKSHEET_ASSIGNMENT_NOT_FOUND")
    void saveAnswers_notOwned_returns404() throws Exception {
        long assignmentStudentId = buildAssignment(FUTURE_DUE_AT);
        long otherStudentId = insertAccount("STUDENT", "submission-test-other", "학생2");
        String otherToken = jwtProvider.issueAccessToken(otherStudentId, UserRole.STUDENT).value();

        String requestJson = """
                {"timeSpentSeconds": 10, "answers": []}
                """;

        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("제출하면 상태가 submitted로 바뀌고, 이미 제출한 뒤 저장·재제출은 409다")
    void submit_marksSubmitted_andBlocksFurtherWrites() throws Exception {
        long assignmentStudentId = buildAssignment(FUTURE_DUE_AT);

        String requestJson = """
                {"timeSpentSeconds": 10, "answers": [
                    {"answerUnitId": %d, "selectedChoiceId": %d, "rawLatex": null, "hasHandwriting": false}
                ]}
                """.formatted(choiceAnswerUnitId, correctChoiceId);
        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/student/assignments/" + assignmentStudentId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("submitted"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.answeredUnits").value(1))
                .andExpect(jsonPath("$.data.totalUnits").value(2));

        // raw JDBC 읽기는 같은 트랜잭션 안에서 Hibernate가 아직 flush하지 않은 값을 볼 수 있어
        // (다른 커넥션이 아니라 같은 트랜잭션의 미반영 상태 문제다), JPA 경로로 재확인한다.
        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("submitted"));

        // 제출 후 답안 저장 → 409
        mockMvc.perform(put("/api/student/assignments/" + assignmentStudentId + "/items/" + worksheetItemId)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_ALREADY_SUBMITTED"));

        // 재제출 → 409
        mockMvc.perform(post("/api/student/assignments/" + assignmentStudentId + "/submit")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SUBMISSION_ALREADY_SUBMITTED"));
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

    private long insertClass(long teacherId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_school_class(academic_year, grade, name, homeroom_teacher_id, display_order)
                VALUES (2026, 1, '테스트반', ?, 0)
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertCurriculumUnit() {
        long majorUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, 'submission-test-major', 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'submission-test-middle', 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'submission-test-sub', 'SUB_UNIT', '소단원', 1, 0)
                RETURNING id
                """, Long.class, middleUnitId);
    }

    private long insertQuestion(long subUnitId, String questionType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', '[]'::jsonb, '검색용 원문 — 화면 표시 금지')
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

    private long insertAnswerUnit(
            long questionId, String unitKey, int displayOrder, String compareMethod, String answerRaw
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_answer_unit(question_id, unit_key, display_order, compare_method, answer_raw)
                VALUES (?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, unitKey, displayOrder, compareMethod, answerRaw);
    }

    private long insertWorksheet(long teacherId, String type) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('제출 테스트 학습지', ?, 'STANDARD', ?, 1, '2', now())
                RETURNING id
                """, Long.class, type, teacherId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, int displayOrder) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, worksheetId, questionId, displayOrder);
    }

    private long insertAssignment(long worksheetId, long classId, String dueAtExpr) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), %s)
                RETURNING id
                """.formatted(dueAtExpr), Long.class, worksheetId, classId);
    }

    private long insertAssignmentStudent(long assignmentId, long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count)
                VALUES (?, ?, 'NOT_STARTED', 0)
                RETURNING id
                """, Long.class, assignmentId, studentId);
    }
}
