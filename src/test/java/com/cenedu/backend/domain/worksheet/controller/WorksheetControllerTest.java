package com.cenedu.backend.domain.worksheet.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/** 문제 보관함 API의 저장·조회·배포·삭제 검증. task_04 §9 표를 그대로 따른다. */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-worksheet-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class WorksheetControllerTest {

    private static final String FUTURE_DUE_AT = "2027-01-01T00:00:00+09:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private long subUnitId;
    private long classId;
    private String teacherToken;
    private String otherTeacherToken;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "worksheet-test-teacher", "테스트교사");
        long otherTeacherId = insertAccount("TEACHER", "worksheet-test-other-teacher", "다른교사");
        teacherToken = issueToken(teacherId);
        otherTeacherToken = issueToken(otherTeacherId);

        long majorUnitId = insertCurriculumUnit(null, "MAJOR_UNIT", "대단원", "worksheet-test-major");
        long middleUnitId = insertCurriculumUnit(majorUnitId, "MIDDLE_UNIT", "중단원",
                "worksheet-test-middle");
        subUnitId = insertCurriculumUnit(middleUnitId, "SUB_UNIT", "소단원", "worksheet-test-sub");

        classId = insertClass(teacherId, "테스트반");
        for (int i = 0; i < 3; i++) {
            long studentId = insertAccount("STUDENT", "worksheet-test-student-" + i, "학생" + i);
            insertStudentProfile(studentId, teacherId);
            insertEnrollment(classId, studentId);
        }
    }

    @Test
    @DisplayName("남의 worksheetId를 조회하면 404가 나고 403이 아니다")
    void getWorksheetDetail_ownedByOtherTeacher_returns404() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));

        mockMvc.perform(get("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_NOT_FOUND"));
    }

    @Test
    @DisplayName("일반 학습에 ESSAY 문항이 섞이면 400 WORKSHEET_TYPE_MISMATCH")
    void createWorksheet_practiceWithEssay_returns400() throws Exception {
        long stepFillId = insertQuestions("STEP_FILL", 1).get(0);
        long essayId = insertQuestions("ESSAY", 1).get(0);
        String requestJson = """
                {
                  "title": "잘못된 연습",
                  "type": "practice",
                  "origin": "manual",
                  "grade": 1,
                  "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [
                    {"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 1},
                    {"subUnitId": %d, "questionType": "essay", "difficulty": "low", "count": 1}
                  ],
                  "items": [
                    {"questionId": %d, "displayOrder": 1, "supportMode": null, "customStage": null},
                    {"questionId": %d, "displayOrder": 2, "supportMode": null, "customStage": null}
                  ]
                }
                """.formatted(subUnitId, subUnitId, stepFillId, essayId);

        postWorksheet(requestJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_TYPE_MISMATCH"));
    }

    @Test
    @DisplayName("genSpec 합계가 문항 수와 다르면 400 WORKSHEET_SPEC_MISMATCH")
    void createWorksheet_genSpecCountMismatch_returns400() throws Exception {
        long questionId = insertQuestions("STEP_FILL", 1).get(0);
        String requestJson = """
                {
                  "title": "스펙 불일치",
                  "type": "practice", "origin": "manual", "grade": 1, "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 5}],
                  "items": [{"questionId": %d, "displayOrder": 1, "supportMode": null, "customStage": null}]
                }
                """.formatted(subUnitId, questionId);

        postWorksheet(requestJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_SPEC_MISMATCH"));
    }

    @Test
    @DisplayName("같은 문항이 두 번 들어오면 400 WORKSHEET_QUESTION_DUPLICATED")
    void createWorksheet_duplicatedQuestion_returns400() throws Exception {
        long questionId = insertQuestions("STEP_FILL", 1).get(0);
        String requestJson = """
                {
                  "title": "중복 문항",
                  "type": "practice", "origin": "manual", "grade": 1, "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 2}],
                  "items": [
                    {"questionId": %d, "displayOrder": 1, "supportMode": null, "customStage": null},
                    {"questionId": %d, "displayOrder": 2, "supportMode": null, "customStage": null}
                  ]
                }
                """.formatted(subUnitId, questionId, questionId);

        postWorksheet(requestJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_QUESTION_DUPLICATED"));
    }

    @Test
    @DisplayName("displayOrder가 1,1이면 400 INVALID_INPUT_VALUE (500 아님)")
    void createWorksheet_duplicatedDisplayOrder_returns400() throws Exception {
        List<Long> questionIds = insertQuestions("STEP_FILL", 2);
        String requestJson = """
                {
                  "title": "순서 중복",
                  "type": "practice", "origin": "manual", "grade": 1, "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 2}],
                  "items": [
                    {"questionId": %d, "displayOrder": 1, "supportMode": null, "customStage": null},
                    {"questionId": %d, "displayOrder": 1, "supportMode": null, "customStage": null}
                  ]
                }
                """.formatted(subUnitId, questionIds.get(0), questionIds.get(1));

        postWorksheet(requestJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("미지의 supportMode 값이면 400 INVALID_INPUT_VALUE (500 아님)")
    void createWorksheet_unknownSupportMode_returns400() throws Exception {
        long questionId = insertQuestions("STEP_FILL", 1).get(0);
        String requestJson = """
                {
                  "title": "잘못된 supportMode",
                  "type": "practice", "origin": "manual", "grade": 1, "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 1}],
                  "items": [{"questionId": %d, "displayOrder": 1, "supportMode": "study", "customStage": null}]
                }
                """.formatted(subUnitId, questionId);

        postWorksheet(requestJson)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("종합평가 7문항 저장 시 앞 6개는 14.28, 마지막은 14.32다")
    void createWorksheet_assessment7Items_evenlyDistributesScore() throws Exception {
        List<Long> questionIds = insertQuestions("MULTIPLE_CHOICE", 7);
        long worksheetId = createWorksheet(teacherToken, assessmentRequestJson(questionIds));

        MvcResult result = mockMvc.perform(get("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn();
        List<Number> maxScores = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.items[*].maxScore");
        List<String> formatted = maxScores.stream()
                .map(number -> new BigDecimal(number.toString()).setScale(2).toPlainString())
                .toList();

        assertThat(formatted.subList(0, 6)).containsOnly("14.28");
        assertThat(formatted.get(6)).isEqualTo("14.32");
    }

    @Test
    @DisplayName("같은 반에 재배포하면 409")
    void assignWorksheet_sameClassTwice_returns409() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));

        assignWorksheet(teacherToken, worksheetId, classId, FUTURE_DUE_AT)
                .andExpect(status().isCreated());
        assignWorksheet(teacherToken, worksheetId, classId, FUTURE_DUE_AT)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_DUPLICATE_ASSIGNMENT"));
    }

    @Test
    @DisplayName("배포된 학습지는 삭제할 수 없다 (409)")
    void deleteWorksheet_alreadyAssigned_returns409() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));
        assignWorksheet(teacherToken, worksheetId, classId, FUTURE_DUE_AT)
                .andExpect(status().isCreated());

        mockMvc.perform(delete("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ALREADY_ASSIGNED"));
    }

    @Test
    @DisplayName("삭제된 학습지를 조회하면 404")
    void getWorksheetDetail_deletedWorksheet_returns404() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));

        mockMvc.perform(delete("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 classId로 배포하면 404 WORKSHEET_CLASS_NOT_OWNED (403 MEMBER_* 아님)")
    void assignWorksheet_otherTeachersClass_returns404() throws Exception {
        long otherTeacherId = insertAccount("TEACHER", "worksheet-test-class-owner", "반주인교사");
        long otherClassId = insertClass(otherTeacherId, "남의반");
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));

        assignWorksheet(teacherToken, worksheetId, otherClassId, FUTURE_DUE_AT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_CLASS_NOT_OWNED"));
    }

    @Test
    @DisplayName("없는 classId로 배포하면 404 WORKSHEET_CLASS_NOT_OWNED (같은 응답)")
    void assignWorksheet_missingClass_returns404() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));

        assignWorksheet(teacherToken, worksheetId, 999_999_999L, FUTURE_DUE_AT)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_CLASS_NOT_OWNED"));
    }

    @Test
    @DisplayName("dueAt이 지난 배포의 상세 조회는 status가 completed다")
    void getWorksheetDetail_pastDueAt_statusCompleted() throws Exception {
        long worksheetId = createWorksheet(teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));
        jdbcTemplate.update("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now() - interval '2 days', now() - interval '1 day')
                """, worksheetId, classId);

        mockMvc.perform(get("/api/teacher/worksheets/" + worksheetId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments[0].status").value("completed"));
    }

    @Test
    @DisplayName("출처가 practice인 맞춤 학습지는 tab=practice 목록에도 포함된다")
    void getWorksheets_customFromPractice_includedInPracticeTab() throws Exception {
        long sourceWorksheetId = createWorksheet(
                teacherToken, practiceRequestJson(insertQuestions("STEP_FILL", 1)));
        MvcResult assignResult = assignWorksheet(teacherToken, sourceWorksheetId, classId, FUTURE_DUE_AT)
                .andExpect(status().isCreated())
                .andReturn();
        long assignmentId = ((Number) JsonPath.read(
                assignResult.getResponse().getContentAsString(), "$.data.assignmentId")).longValue();

        List<Long> customQuestionIds = insertQuestions("STEP_FILL", 1);
        String customRequestJson = """
                {
                  "title": "맞춤 학습지",
                  "type": "practice",
                  "origin": "custom",
                  "grade": 1,
                  "semester": "common",
                  "sourceAssignmentId": %d,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": 1}],
                  "items": [%s]
                }
                """.formatted(assignmentId, subUnitId, itemsJson(customQuestionIds));
        long customWorksheetId = createWorksheet(teacherToken, customRequestJson);

        mockMvc.perform(get("/api/teacher/worksheets")
                        .queryParam("tab", "practice")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.data.worksheets[?(@.worksheetId == %d)]".formatted(customWorksheetId))
                        .exists());
    }

    private ResultActions postWorksheet(String requestJson) throws Exception {
        return mockMvc.perform(post("/api/teacher/worksheets")
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));
    }

    private long createWorksheet(String token, String requestJson) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/teacher/worksheets")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isCreated())
                .andReturn();
        return ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.worksheetId")).longValue();
    }

    private ResultActions assignWorksheet(String token, long worksheetId, long classId, String dueAt)
            throws Exception {
        String requestJson = """
                {"classId": %d, "dueAt": "%s"}
                """.formatted(classId, dueAt);
        return mockMvc.perform(post("/api/teacher/worksheets/" + worksheetId + "/assignments")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson));
    }

    private String itemsJson(List<Long> questionIds) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < questionIds.size(); i++) {
            if (i > 0) {
                items.append(",");
            }
            items.append("""
                    {"questionId": %d, "displayOrder": %d, "supportMode": null, "customStage": null}
                    """.formatted(questionIds.get(i), i + 1));
        }
        return items.toString();
    }

    private String practiceRequestJson(List<Long> questionIds) {
        return """
                {
                  "title": "연습 학습지",
                  "type": "practice",
                  "origin": "manual",
                  "grade": 1,
                  "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "step", "difficulty": "low", "count": %d}],
                  "items": [%s]
                }
                """.formatted(subUnitId, questionIds.size(), itemsJson(questionIds));
    }

    private String assessmentRequestJson(List<Long> questionIds) {
        return """
                {
                  "title": "종합평가",
                  "type": "assessment",
                  "origin": "manual",
                  "grade": 1,
                  "semester": "common",
                  "sourceAssignmentId": null,
                  "genSpec": [{"subUnitId": %d, "questionType": "choice", "difficulty": "low", "count": %d}],
                  "items": [%s]
                }
                """.formatted(subUnitId, questionIds.size(), itemsJson(questionIds));
    }

    private List<Long> insertQuestions(String questionType, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Long id = jdbcTemplate.queryForObject("""
                    INSERT INTO problem_question(
                        source_type, sub_unit_id, difficulty, question_type,
                        presentation, content_blocks, prompt_text
                    ) VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', '[]'::jsonb, ?)
                    RETURNING id
                    """, Long.class, subUnitId, questionType, "테스트 문항");
            ids.add(id);
        }
        return ids;
    }

    private long insertCurriculumUnit(Long parentId, String unitLevel, String name, String externalKey) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, ?, ?, ?, 1, 0)
                RETURNING id
                """, Long.class, parentId, externalKey, unitLevel, name);
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

    private long insertClass(long teacherId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_school_class(academic_year, grade, name, homeroom_teacher_id, display_order)
                VALUES (2026, 1, ?, ?, 0)
                RETURNING id
                """, Long.class, name, teacherId);
    }

    private void insertEnrollment(long classId, long studentId) {
        jdbcTemplate.update("""
                INSERT INTO member_class_enrollment(class_id, student_id)
                VALUES (?, ?)
                """, classId, studentId);
    }

    private String issueToken(long memberId) {
        return jwtProvider.issueAccessToken(memberId, UserRole.TEACHER).value();
    }
}
