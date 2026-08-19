package com.cenedu.backend.domain.worksheet.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.submission.service.SubmissionImageService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 복습 화면이 학생 본인의 필기 이미지를 받는지 확인한다.
 *
 * <p>{@link StudentResultControllerTest}는 S3를 꺼 둔 컨텍스트라 이 경로를 아예 타지 않는다.
 * 학생 결과 화면은 원래 S3를 쓰지 않던 곳이라, 배선이 틀리면 화면 전체가 죽는다. 그래서
 * <b>인자까지 맞춘 스텁</b>을 쓴다 — 역할이나 ID 순서를 잘못 넘기면 스텁이 안 걸려 실패한다.
 *
 * <p>자격증명을 프로퍼티로 박는다. {@code .env} 에 기대면 그 파일이 없는 환경(클론 직후, 워크트리)에서
 * {@code @NotBlank} 바인딩이 깨져 컨텍스트가 아예 안 뜬다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        "app.storage.s3.enabled=true",
        "app.storage.s3.region=ap-northeast-2",
        "app.storage.s3.problem-bucket=problem-bucket",
        "app.storage.s3.answer-bucket=answer-bucket",
        "app.storage.s3.access-key-id=test-access-key",
        "app.storage.s3.secret-access-key=test-secret-key"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class StudentResultHandwritingUrlTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SubmissionImageService submissionImageService;

    private long studentId;
    private long teacherId;
    private long subUnitId;
    private String studentToken;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "hw-test-teacher", "테스트교사");
        studentId = insertAccount("STUDENT", "hw-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();
        subUnitId = insertCurriculumUnit();
    }

    @Test
    @DisplayName("복습 화면은 학생 본인이 쓴 필기 이미지 URL을 받는다")
    void getResult_returnsOwnHandwritingUrl() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        long worksheetId = insertWorksheet();
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId);
        insertHandwritingAnswer(assignmentStudentId, answerUnitId);

        // 본인(STUDENT)으로 조회해야 한다 — 교사 역할로 넘기면 이 스텁이 걸리지 않는다.
        when(submissionImageService.createGetUrls(
                studentId, UserRole.STUDENT, assignmentStudentId, List.of(answerUnitId)))
                .thenReturn(Map.of(answerUnitId, "https://example.com/my-handwriting"));

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].hasHandwriting").value(true))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].handwritingUrl")
                        .value("https://example.com/my-handwriting"));
    }

    @Test
    @DisplayName("버킷에 이미지가 없어 URL을 못 만든 칸도 화면은 열린다")
    void getResult_missingObject_stillReturnsResult() throws Exception {
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        long worksheetId = insertWorksheet();
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long classId = insertClass();
        long assignmentId = insertAssignment(worksheetId, classId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId);
        insertHandwritingAnswer(assignmentStudentId, answerUnitId);

        // 업로드 실패·수명주기 삭제로 객체가 없으면 그 칸만 결과에서 빠진다.
        when(submissionImageService.createGetUrls(
                studentId, UserRole.STUDENT, assignmentStudentId, List.of(answerUnitId)))
                .thenReturn(Map.of());

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId + "/result")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                // 이미지가 있었다는 사실 자체는 DB 가 알고 있으므로 플래그는 그대로 true 다.
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].hasHandwriting").value(true))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].handwritingUrl").doesNotExist());
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

    private long insertCurriculumUnit() {
        long majorUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, 'hw-test-major', 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'hw-test-middle', 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'hw-test-sub', 'SUB_UNIT', '소단원', 1, 0)
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
                    question_id, unit_key, display_order, compare_method, answer_raw)
                VALUES (?, 'MAIN', 0, ?, ?)
                RETURNING id
                """, Long.class, questionId, compareMethod, answerRaw);
    }

    private long insertWorksheet() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('필기 테스트 학습지', 'GENERAL_LEARNING', 'STANDARD', ?, 1, '2', now())
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertWorksheetItem(long worksheetId, long questionId, BigDecimal maxScore) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, max_score)
                VALUES (?, ?, 1, ?)
                RETURNING id
                """, Long.class, worksheetId, questionId, maxScore);
    }

    private long insertClass() {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_school_class(academic_year, grade, name, homeroom_teacher_id, display_order)
                VALUES (2026, 1, '테스트반', ?, 0)
                RETURNING id
                """, Long.class, teacherId);
    }

    private long insertAssignment(long worksheetId, long classId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    private long insertAssignmentStudent(long assignmentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(
                    assignment_id, student_id, status, progress_count, released_at)
                VALUES (?, ?, 'GRADED', 1, now())
                RETURNING id
                """, Long.class, assignmentId, studentId);
    }

    private void insertHandwritingAnswer(long assignmentStudentId, long answerUnitId) {
        jdbcTemplate.update("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, raw_latex,
                    answer_image_ref, compare_method, grading_status, final_score)
                VALUES (?, ?, 'HANDWRITING', '7', 'answers/1/units/1', 'VALUE', 'GRADED', 10.00)
                """, assignmentStudentId, answerUnitId);
    }
}
