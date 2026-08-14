package com.cenedu.backend.domain.worksheet.controller;

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
 * task_05 단계 1(조회 2종) 검증. 명세 5-6/5-7절의 "절대 내보내지 않는 것"과 소유권 검증을 중심으로 본다.
 *
 * <p>쿼리 수는 여기서 단언하지 않는다 — {@code logging.level.org.hibernate.SQL=DEBUG}로 수동 실행해
 * 최종 보고에 남긴다({@code application.yaml}에 영구히 켜두지 않는다).
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-student-worksheet-controller-test-secret-32b",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class StudentWorksheetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long studentId;
    private String studentToken;
    private long choiceQuestionId;
    private long stepQuestionId;
    private long assignmentStudentId;

    @BeforeEach
    void setUp() {
        long teacherId = insertAccount("TEACHER", "student-api-test-teacher", "테스트교사");
        studentId = insertAccount("STUDENT", "student-api-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();

        long subUnitId = insertCurriculumUnit();

        // TEXT + FIGURE + TABLE 세 블록 종류를 한 문항에 다 넣어 stage-1 측정 요구 4번을 겸한다.
        choiceQuestionId = insertQuestion(subUnitId, "MULTIPLE_CHOICE", """
                [
                  {"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"다음 중 소수인 것을 고르시오."},
                  {"blockId":"Q-IMG-1","blockKind":"FIGURE","displayOrder":1,"assetRef":"F1","text":"부채꼴 그림"},
                  {"blockId":"T2","blockKind":"TABLE","displayOrder":2,"text":"","markup":"<table><tr><td>1</td></tr></table>"}
                ]
                """);
        long choiceId1 = insertChoice(choiceQuestionId, 1, "1");
        insertChoice(choiceQuestionId, 2, "4");
        insertAnswerUnit(choiceQuestionId, null, "MAIN", 0, "CHOICE", String.valueOf(choiceId1));

        stepQuestionId = insertQuestion(subUnitId, "STEP_FILL", """
                [{"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"36을 소인수분해하시오."}]
                """);
        long stepId = insertStep(stepQuestionId, 0, "풀이 과정 1", """
                [{"type":"TEXT","value":"36 = 2 ×"},{"type":"BLANK","unitKey":"B1"}]
                """);
        insertAnswerUnit(stepQuestionId, stepId, "B1", 0, "VALUE", "18");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING", "STANDARD");
        insertWorksheetItem(worksheetId, choiceQuestionId, 1);
        insertWorksheetItem(worksheetId, stepQuestionId, 2);

        long classId = insertClass(teacherId);
        long assignmentId = insertAssignment(worksheetId, classId);
        assignmentStudentId = insertAssignmentStudent(assignmentId, studentId);
    }

    @Test
    @DisplayName("목록에 배정이 나오고 진행 상태·단위 수가 명세대로 계산된다")
    void getAssignments_returnsAssignment() throws Exception {
        mockMvc.perform(get("/api/student/assignments")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments[0].assignmentStudentId").value(assignmentStudentId))
                .andExpect(jsonPath("$.data.assignments[0].type").value("practice"))
                .andExpect(jsonPath("$.data.assignments[0].origin").value("standard"))
                .andExpect(jsonPath("$.data.assignments[0].category").value("homework"))
                .andExpect(jsonPath("$.data.assignments[0].status").value("not-started"))
                .andExpect(jsonPath("$.data.assignments[0].doneUnits").value(0))
                // GENERAL_LEARNING → 문항들의 answer_unit 수 합(1+1=2), 문항 수(2)가 아니다.
                .andExpect(jsonPath("$.data.assignments[0].totalUnits").value(2))
                .andExpect(jsonPath("$.data.assignments[0].resultReady").value(false))
                .andExpect(jsonPath("$.data.assignments[0].stages").doesNotExist());
    }

    @Test
    @DisplayName("상세 조회에 TEXT/FIGURE/TABLE 블록이 모두 실리고 정답·설명은 안 나간다")
    void getAssignmentDetail_rendersAllBlockKinds_withoutLeakingAnswers() throws Exception {
        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("not-started"))
                .andExpect(jsonPath("$.data.items[0].format").value("choice"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[0].blockKind").value("TEXT"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[1].blockKind").value("FIGURE"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[1].imageUrl")
                        .value("/api/images/problems/" + choiceQuestionId + "/assets/F1"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[2].blockKind").value("TABLE"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[2].markup").exists())
                .andExpect(jsonPath("$.data.items[0].choices").isArray())
                .andExpect(jsonPath("$.data.items[0].steps").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].inputMode").value("CHOICE"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].saved.selectedChoiceId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].saved.hasHandwriting").value(false))
                // 절대 내보내지 않는 것 (명세 5.3)
                .andExpect(jsonPath("$.data.items[0].promptText").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].choices[0].isCorrect").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].answer").doesNotExist())
                // STEP_FILL 문항: steps는 있고 choices는 없어야 한다. segments type은 소문자, unitKey는
                // answerUnitId로 풀려야 한다(실측: DB는 대문자/unitKey, 프론트는 소문자/answerUnitId 기대).
                .andExpect(jsonPath("$.data.items[1].format").value("step"))
                .andExpect(jsonPath("$.data.items[1].choices").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].steps[0].segments[0].type").value("text"))
                .andExpect(jsonPath("$.data.items[1].steps[0].segments[1].type").value("blank"))
                .andExpect(jsonPath("$.data.items[1].steps[0].segments[1].answerUnitId")
                        .value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.data.items[1].steps[0].instruction").doesNotExist());
    }

    @Test
    @DisplayName("남의 assignmentStudentId를 조회하면 404 WORKSHEET_ASSIGNMENT_NOT_FOUND (403 아님)")
    void getAssignmentDetail_notOwned_returns404() throws Exception {
        long otherStudentId = insertAccount("STUDENT", "student-api-test-other", "학생2");
        String otherToken = jwtProvider.issueAccessToken(otherStudentId, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("존재하지 않는 assignmentStudentId도 같은 404")
    void getAssignmentDetail_missing_returns404() throws Exception {
        mockMvc.perform(get("/api/student/assignments/999999999")
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    @DisplayName("두 엔드포인트가 /v3/api-docs에 summary·allowableValues와 함께 노출된다")
    void apiDocs_exposesBothEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/student/assignments'].get.summary").exists())
                .andExpect(jsonPath("$.paths['/api/student/assignments/{assignmentStudentId}'].get.summary")
                        .exists())
                .andExpect(jsonPath(
                        "$.components.schemas.StudentAssignmentResponse.properties.status.enum").isArray());
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
                VALUES (null, 'student-api-test-major', 'MAJOR_UNIT', '대단원', 1, 0)
                RETURNING id
                """, Long.class);
        long middleUnitId = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'student-api-test-middle', 'MIDDLE_UNIT', '중단원', 1, 0)
                RETURNING id
                """, Long.class, majorUnitId);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'student-api-test-sub', 'SUB_UNIT', '소단원', 1, 0)
                RETURNING id
                """, Long.class, middleUnitId);
    }

    private long insertQuestion(long subUnitId, String questionType, String contentBlocks) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', ?::jsonb, '검색용 원문 — 화면 표시 금지')
                RETURNING id
                """, Long.class, subUnitId, questionType, contentBlocks);
    }

    private long insertChoice(long questionId, int displayOrder, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_choice(question_id, display_order, content)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, questionId, displayOrder, content);
    }

    private long insertStep(long questionId, int displayOrder, String label, String segments) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_step(question_id, display_order, label, segments)
                VALUES (?, ?, ?, ?::jsonb)
                RETURNING id
                """, Long.class, questionId, displayOrder, label, segments);
    }

    private void insertAnswerUnit(
            long questionId, Long stepId, String unitKey, int displayOrder, String compareMethod, String answerRaw
    ) {
        jdbcTemplate.update("""
                INSERT INTO problem_answer_unit(question_id, step_id, unit_key, display_order, compare_method, answer_raw)
                VALUES (?, ?, ?, ?, ?, ?)
                """, questionId, stepId, unitKey, displayOrder, compareMethod, answerRaw);
    }

    private long insertWorksheet(long teacherId, String type, String origin) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('학생 API 테스트 학습지', ?, ?, ?, 1, '2', now())
                RETURNING id
                """, Long.class, type, origin, teacherId);
    }

    private void insertWorksheetItem(long worksheetId, long questionId, int displayOrder) {
        jdbcTemplate.update("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order)
                VALUES (?, ?, ?)
                """, worksheetId, questionId, displayOrder);
    }

    private long insertAssignment(long worksheetId, long classId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, classId);
    }

    private long insertAssignmentStudent(long assignmentId, long studentId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(assignment_id, student_id, status, progress_count)
                VALUES (?, ?, 'NOT_STARTED', 0)
                RETURNING id
                """, Long.class, assignmentId, studentId);
    }
}
