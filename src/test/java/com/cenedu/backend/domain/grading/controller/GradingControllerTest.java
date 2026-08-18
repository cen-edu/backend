package com.cenedu.backend.domain.grading.controller;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/**
 * 채점 API 의 조회·수정·확정 검증(task_06 §11).
 *
 * <p>자동채점 실행은 여기서 다루지 않는다 — 칸 하나가 {@code REQUIRES_NEW} 트랜잭션이고 배치가
 * {@code @Async}라, {@code @Transactional} 테스트가 만든 픽스처를 그 경로에서 볼 수 없다.
 * 그쪽은 {@code GradingAutoGradingTest}가 커밋된 데이터로 검증한다.
 *
 * <p>{@code S3_ENABLED} 기본값이 {@code false}라 {@code SubmissionImageService} 빈이 아예 없다.
 * 상세 조회가 그 상태에서도 200 을 내는지가 검증 대상 중 하나다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        // 명시하지 않으면 .env 의 S3_ENABLED 가 새어들어와 이 테스트가 실제 버킷에 headObject 를
        // 날린다. 그러면 "S3 가 꺼진 환경" 이라는 이름과 달리 켜진 채로 돌게 된다.
        "app.storage.s3.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class GradingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private String teacherToken;
    private long otherTeacherId;
    private String otherTeacherToken;
    private long studentId;
    private String studentToken;
    private long classId;
    private long subUnitId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "grading-test-teacher", "채점교사");
        otherTeacherId = insertAccount("TEACHER", "grading-test-other", "남의교사");
        studentId = insertAccount("STUDENT", "grading-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        teacherToken = jwtProvider.issueAccessToken(teacherId, UserRole.TEACHER).value();
        otherTeacherToken = jwtProvider.issueAccessToken(otherTeacherId, UserRole.TEACHER).value();
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();
        classId = insertClass();
        subUnitId = insertCurriculumUnit();
    }

    @Test
    @DisplayName("남의 배포는 403이 아니라 404다 — 존재 여부를 흘리지 않는다")
    void getScoreTable_otherTeacher_returns404() throws Exception {
        long assignmentId = insertAssignment(insertWorksheet("COMPREHENSIVE_ASSESSMENT"));

        mockMvc.perform(get("/api/teacher/grading/" + assignmentId)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GRADING_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("S3가 꺼진 환경에서도 상세 조회는 200이고 handwritingUrl은 null이다")
    void getStudentDetail_withoutS3_returnsNullHandwritingUrl() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        // 이미지 참조가 있어도 빈이 없으면 URL 을 만들지 못한다 — 그래도 터지면 안 된다.
        insertAnswer(assignmentStudentId, answerUnitId, "VALUE", "GRADED",
                new BigDecimal("10.00"), "answers/1/1");

        mockMvc.perform(get("/api/teacher/grading/" + assignmentId
                        + "/students/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].handwritingUrl").doesNotExist())
                .andExpect(jsonPath("$.data.studentNumber").doesNotExist());
    }

    @Test
    @DisplayName("교사 전용 필드는 학생 결과 조회에 나가지 않는다")
    void studentResult_hasNoTeacherOnlyFields() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "GRADED");
        jdbcTemplate.update("UPDATE worksheet_assignment_student SET released_at = now() WHERE id = ?",
                assignmentStudentId);
        insertAnswer(assignmentStudentId, answerUnitId, "VALUE", "GRADED",
                new BigDecimal("10.00"), null);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].autoScore").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].compareMethod").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].failureReason").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].rubric[0].evidence").doesNotExist());
    }

    @Test
    @DisplayName("미채점이 남은 상태의 확정은 409다 — 버튼 비활성은 UX이지 검증이 아니다")
    void release_withUngradedAnswer_returns409() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        insertAnswer(assignmentStudentId, answerUnitId, "VALUE", "NOT_GRADED", null, null);

        mockMvc.perform(post("/api/teacher/grading/" + assignmentId + "/release")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GRADING_INCOMPLETE"));
    }

    @Test
    @DisplayName("서술형 칸만 FAILED로 남아도 확정은 막힌다")
    void release_withFailedRubricAnswer_returns409() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, "RUBRIC", null);
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        insertAnswer(assignmentStudentId, answerUnitId, "RUBRIC", "FAILED", null, null);

        mockMvc.perform(post("/api/teacher/grading/" + assignmentId + "/release")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GRADING_INCOMPLETE"));
    }

    @Test
    @DisplayName("서술형을 rubricChecks로 채우면 서버가 배점을 합산하고 확정이 열린다")
    void patchRubricChecks_thenRelease_succeeds() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, "RUBRIC", null);
        long rubricA = insertRubricItem(questionId, 0, "풀이 과정이 드러나 있음", 4);
        long rubricB = insertRubricItem(questionId, 1, "답이 맞음", 6);
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        long answerId = insertAnswer(assignmentStudentId, answerUnitId, "RUBRIC", "FAILED", null, null);

        // 첫 기준만 충족 → 서버가 4.00 을 계산해야 한다. 프론트가 보낸 점수는 받지 않는다.
        mockMvc.perform(patch("/api/teacher/grading/" + assignmentId + "/answers/" + answerId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rubricChecks":[
                                  {"rubricItemId":%d,"satisfied":true},
                                  {"rubricItemId":%d,"satisfied":false}]}
                                """.formatted(rubricA, rubricB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.finalScore").value(4.00))
                .andExpect(jsonPath("$.data.gradedBy").value("teacher"))
                .andExpect(jsonPath("$.data.itemResult").value("partial"));

        mockMvc.perform(post("/api/teacher/grading/" + assignmentId + "/release")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("confirmed"))
                .andExpect(jsonPath("$.data.releasedStudentCount").value(1));
    }

    @Test
    @DisplayName("다른 문항의 채점 기준을 보내면 400 GRADING_RUBRIC_ITEM_MISMATCH")
    void patchRubricChecks_foreignRubricItem_returns400() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, "RUBRIC", null);
        long foreignRubricId = insertRubricItem(insertQuestion("ESSAY"), 0, "남의 기준", 5);
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        long answerId = insertAnswer(assignmentStudentId, answerUnitId, "RUBRIC", "FAILED", null, null);

        mockMvc.perform(patch("/api/teacher/grading/" + assignmentId + "/answers/" + answerId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rubricChecks":[{"rubricItemId":%d,"satisfied":true}]}
                                """.formatted(foreignRubricId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GRADING_RUBRIC_ITEM_MISMATCH"));
    }

    @Test
    @DisplayName("자동채점값이 없는 칸은 resetToAuto로 되돌릴 수 없다")
    void patchResetToAuto_withoutAutoScore_returns400() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, "RUBRIC", null);
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        long answerId = insertAnswer(assignmentStudentId, answerUnitId, "RUBRIC", "FAILED", null, null);

        mockMvc.perform(patch("/api/teacher/grading/" + assignmentId + "/answers/" + answerId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToAuto\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GRADING_SCORE_OUT_OF_RANGE"));
    }

    // ===== 픽스처 =====

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
                VALUES (2026, 1, '채점테스트반', ?, 0)
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertCurriculumUnit() {
        long majorUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, 'grading-test-major', 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'grading-test-middle', 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'grading-test-sub', 'SUB_UNIT', '소단원', 1, 0)
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

    private long insertAnswerUnit(long questionId, String compareMethod, String answerRaw) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_answer_unit(
                    question_id, unit_key, display_order, compare_method, answer_raw, answer_normalized)
                VALUES (?, 'MAIN', 0, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, compareMethod, answerRaw, answerRaw);
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
                VALUES ('채점 테스트 학습지', ?, 'STANDARD', ?, 1, 'COMMON', now())
                RETURNING id
                """, Long.class, type, teacherId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, BigDecimal maxScore) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, max_score)
                VALUES (?, ?, 1, ?)
                RETURNING id
                """, Long.class, worksheetId, questionId, maxScore);
    }

    private long insertAssignment(long worksheetId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    private long insertAssignmentStudent(long assignmentId, String status) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count)
                VALUES (?, ?, ?, 1)
                RETURNING id
                """, Long.class, assignmentId, studentId, status);
    }

    private long insertAnswer(long assignmentStudentId, long answerUnitId, String compareMethod,
                              String gradingStatus, BigDecimal score, String answerImageRef) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, raw_latex,
                    auto_score, final_score, answer_image_ref, compare_method, grading_status)
                VALUES (?, ?, 'HANDWRITING', '7', ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, assignmentStudentId, answerUnitId, score, score,
                answerImageRef, compareMethod, gradingStatus);
    }
}
