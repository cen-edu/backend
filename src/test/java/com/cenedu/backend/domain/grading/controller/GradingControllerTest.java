package com.cenedu.backend.domain.grading.controller;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

    @Autowired
    private ObjectMapper objectMapper;

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

    // ---------- 평가 결과 목록 ----------

    /**
     * 학생 A 1차·2차, 학생 B 1차. B가 <b>가장 늦게</b> 배정돼 배정일 축이면 3차로 나온다.
     *
     * @return 픽스처 ID 묶음
     */
    private CustomFixture customLearningFixture() {
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        long assignmentId = insertAssignment(worksheetId);
        // 표시 순번은 이름순이라 학생1 이 앞이다(학생1 → students[0], 학생2 → students[1]).
        long seoyun = insertStudent("grading-test-student-2", "학생2");
        insertAssignmentStudent(assignmentId, studentId, "GRADED", 1, true, true);
        insertAssignmentStudent(assignmentId, seoyun, "GRADED", 1, true, true);

        long first = insertCustomWorksheet("A 1차", "GENERAL_LEARNING", assignmentId, worksheetId);
        long second = insertCustomWorksheet("A 2차", "GENERAL_LEARNING", assignmentId, first);
        long other = insertCustomWorksheet("B 1차", "COMPREHENSIVE_ASSESSMENT", assignmentId, worksheetId);

        long firstAssignment = insertStudentAssignment(first, studentId, 10);
        long secondAssignment = insertStudentAssignment(second, studentId, 3);
        long otherAssignment = insertStudentAssignment(other, seoyun, 1);
        insertAssignmentStudent(firstAssignment, studentId, "SUBMITTED", 3, true, false);
        insertAssignmentStudent(secondAssignment, studentId, "NOT_STARTED", 0, false, false);
        insertAssignmentStudent(otherAssignment, seoyun, "GRADED", 5, true, true);

        return new CustomFixture(worksheetId, assignmentId, first, second, other,
                firstAssignment, secondAssignment, otherAssignment);
    }

    private record CustomFixture(long rootWorksheetId, long rootAssignmentId,
                                 long firstWorksheetId, long secondWorksheetId, long otherWorksheetId,
                                 long firstAssignmentId, long secondAssignmentId,
                                 long otherAssignmentId) {
    }

    @Test
    @DisplayName("맞춤 배정은 최상위에 형제로 나오지 않고 원본 아래 학생별로 들어간다")
    void getWorksheets_customNestsUnderSource() throws Exception {
        CustomFixture f = customLearningFixture();

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheets.length()").value(1))
                .andExpect(jsonPath("$.data.worksheets[0].assignmentId").value(f.rootAssignmentId()))
                .andExpect(jsonPath("$.data.worksheets[0].origin").value("standard"))
                // 맞춤 배정 ID 가 최상위에 형제로 없다.
                .andExpect(jsonPath("$.data.worksheets[?(@.assignmentId == "
                        + f.firstAssignmentId() + ")]").isEmpty())
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.studentCount").value(2))
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.sessionCount").value(3))
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.maxSessionNumber").value(2));
    }

    @Test
    @DisplayName("차수는 계보의 깊이다 — 가장 늦게 배정된 학생의 첫 맞춤도 1차다")
    void getWorksheets_sessionNumberFollowsLineage() throws Exception {
        CustomFixture f = customLearningFixture();
        String tree = "$.data.worksheets[0].customLearning";

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 학생1(displayNumber 1)이 1차·2차를 받았다.
                .andExpect(jsonPath(tree + ".students[0].name").value("학생1"))
                .andExpect(jsonPath(tree + ".students[0].sessions.length()").value(2))
                .andExpect(jsonPath(tree + ".students[0].sessions[0].sessionNumber").value(1))
                .andExpect(jsonPath(tree + ".students[0].sessions[0].worksheetId")
                        .value(f.firstWorksheetId()))
                .andExpect(jsonPath(tree + ".students[0].sessions[0].parentWorksheetId")
                        .value(f.rootWorksheetId()))
                .andExpect(jsonPath(tree + ".students[0].sessions[1].sessionNumber").value(2))
                .andExpect(jsonPath(tree + ".students[0].sessions[1].parentWorksheetId")
                        .value(f.firstWorksheetId()))
                // 학생2 는 가장 늦게 받았다. 배정일 축이면 3차로 나온다.
                .andExpect(jsonPath(tree + ".students[1].name").value("학생2"))
                .andExpect(jsonPath(tree + ".students[1].sessions.length()").value(1))
                .andExpect(jsonPath(tree + ".students[1].sessions[0].sessionNumber").value(1))
                .andExpect(jsonPath(tree + ".students[1].sessions[0].parentWorksheetId")
                        .value(f.rootWorksheetId()));
    }

    @Test
    @DisplayName("맞춤 차수는 채점 축과 진행 축을 함께 내린다 — 학생이 한 명이라 status만으로는 못 가른다")
    void getWorksheets_customSessionHasBothStatusAxes() throws Exception {
        customLearningFixture();
        String sessions = "$.data.worksheets[0].customLearning.students[0].sessions";

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 1차는 제출·미채점, 2차는 미시작. status 는 둘 다 grading 이다.
                .andExpect(jsonPath(sessions + "[0].status").value("grading"))
                .andExpect(jsonPath(sessions + "[0].studentStatus").value("submitted"))
                .andExpect(jsonPath(sessions + "[0].submittedAt").exists())
                .andExpect(jsonPath(sessions + "[1].status").value("grading"))
                .andExpect(jsonPath(sessions + "[1].studentStatus").value("not-started"))
                .andExpect(jsonPath(sessions + "[1].submittedAt").doesNotExist())
                // 일반 학습은 채점 축이 없다.
                .andExpect(jsonPath(sessions + "[0].type").value("practice"))
                .andExpect(jsonPath(sessions + "[0].grading").doesNotExist())
                .andExpect(jsonPath(sessions + "[0].score").doesNotExist())
                .andExpect(jsonPath(sessions + "[0].doneUnits").value(3));
    }

    @Test
    @DisplayName("종합평가 맞춤은 grading·score가 나가고 확정된 차수는 confirmed다")
    void getWorksheets_assessmentCustomHasScore() throws Exception {
        CustomFixture f = customLearningFixture();
        jdbcTemplate.update("""
                UPDATE worksheet_assignment_student SET total_score = 72.00, released_at = now()
                WHERE assignment_id = ?
                """, f.otherAssignmentId());
        String session = "$.data.worksheets[0].customLearning.students[1].sessions[0]";

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath(session + ".type").value("assessment"))
                .andExpect(jsonPath(session + ".status").value("confirmed"))
                .andExpect(jsonPath(session + ".grading").value("done"))
                .andExpect(jsonPath(session + ".score").value(72.00))
                .andExpect(jsonPath(session + ".assignmentId").value(f.otherAssignmentId()));
    }

    @Test
    @DisplayName("반 필터를 걸어도 맞춤은 사라지지 않는다 — 맞춤은 class_id가 null이다")
    void getWorksheets_classFilterKeepsCustom() throws Exception {
        customLearningFixture();

        mockMvc.perform(get("/api/teacher/grading")
                        .param("classId", String.valueOf(classId))
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheets.length()").value(1))
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.sessionCount").value(3));
    }

    @Test
    @DisplayName("맞춤이 없으면 customLearning은 null이고 기존 필드는 그대로다")
    void getWorksheets_withoutCustom_keepsExistingFields() throws Exception {
        long assignmentId = insertAssignment(insertWorksheet("COMPREHENSIVE_ASSESSMENT"));
        insertAssignmentStudent(assignmentId, studentId, "SUBMITTED", 1, true, false);

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheets[0].customLearning").doesNotExist())
                .andExpect(jsonPath("$.data.worksheets[0].className").value("채점테스트반"))
                .andExpect(jsonPath("$.data.worksheets[0].studentCount").value(1))
                .andExpect(jsonPath("$.data.worksheets[0].submittedCount").value(1))
                .andExpect(jsonPath("$.data.worksheets[0].gradedCount").value(0))
                .andExpect(jsonPath("$.data.worksheets[0].pendingCount").value(1));
    }

    @Test
    @DisplayName("소프트 삭제된 맞춤 학습지는 트리에서 빠진다")
    void getWorksheets_softDeletedCustomExcluded() throws Exception {
        CustomFixture f = customLearningFixture();
        jdbcTemplate.update("UPDATE worksheet SET deleted_at = now() WHERE id = ?",
                f.secondWorksheetId());

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.sessionCount").value(2))
                .andExpect(jsonPath("$.data.worksheets[0].customLearning.maxSessionNumber").value(1));
    }

    @Test
    @DisplayName("남의 교사에게는 원본도 맞춤도 보이지 않는다")
    void getWorksheets_otherTeacherSeesNothing() throws Exception {
        customLearningFixture();

        mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + otherTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheets").isEmpty());
    }

    @Test
    @DisplayName("학습 현황과 평가 결과가 같은 학습지를 같은 차수·같은 진행값으로 본다")
    void getWorksheets_agreesWithLearningStatus() throws Exception {
        CustomFixture f = customLearningFixture();

        String grading = mockMvc.perform(get("/api/teacher/grading")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String learningStatus = mockMvc.perform(
                        get("/api/teacher/learning-status/" + f.rootAssignmentId() + "/custom-learning")
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 차수: 평가 결과는 학생 아래 차수, 학습 현황은 학습지 카드. 축이 달라도 값은 같아야 한다.
        assertThat(sessionNumbersFromGrading(grading))
                .isEqualTo(sessionNumbersFromLearningStatus(learningStatus))
                .containsEntry(f.firstWorksheetId(), 1)
                .containsEntry(f.secondWorksheetId(), 2)
                .containsEntry(f.otherWorksheetId(), 1);

        // 진행값: 한 화면만 고치면 어긋나는데, 각 화면만 보면 정상으로 보인다.
        assertThat(progressFromGrading(grading)).isEqualTo(progressFromLearningStatus(learningStatus));
    }

    /** 평가 결과 트리에서 학습지 ID → 차수. */
    private Map<Long, Integer> sessionNumbersFromGrading(String body) throws Exception {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (JsonNode session : gradingSessions(body)) {
            result.put(session.get("worksheetId").asLong(), session.get("sessionNumber").asInt());
        }
        return result;
    }

    /** 학습 현황 카드에서 학습지 ID → 차수. */
    private Map<Long, Integer> sessionNumbersFromLearningStatus(String body) throws Exception {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (JsonNode card : objectMapper.readTree(body).path("data").path("worksheets")) {
            result.put(card.get("worksheetId").asLong(), card.get("sessionNumber").asInt());
        }
        return result;
    }

    /**
     * 평가 결과 트리에서 배정 ID → 진행·채점값.
     *
     * <p>진행 축은 {@code studentStatus}에서 읽는다. 평가 결과의 {@code status}는 원본 행과 이름을
     * 맞춘 <b>채점 축</b>(grading/graded/confirmed)이라 학습 현황의 {@code status}와 뜻이 다르다.
     */
    private Map<Long, String> progressFromGrading(String body) throws Exception {
        Map<Long, String> result = new LinkedHashMap<>();
        for (JsonNode session : gradingSessions(body)) {
            result.put(session.get("assignmentId").asLong(), progressOf(session, "studentStatus"));
        }
        return result;
    }

    /** 학습 현황 카드에서 배정 ID → 진행·채점값. 이쪽 {@code status}가 진행 축이다. */
    private Map<Long, String> progressFromLearningStatus(String body) throws Exception {
        Map<Long, String> result = new LinkedHashMap<>();
        for (JsonNode card : objectMapper.readTree(body).path("data").path("worksheets")) {
            for (JsonNode student : card.path("students")) {
                result.put(student.get("assignmentId").asLong(), progressOf(student, "status"));
            }
        }
        return result;
    }

    private String progressOf(JsonNode node, String progressField) {
        return node.path(progressField).asText() + "/" + node.path("doneUnits").asInt()
                + "/" + node.path("grading").asText("null")
                + "/" + node.path("score").asText("null");
    }

    private List<JsonNode> gradingSessions(String body) throws Exception {
        List<JsonNode> sessions = new ArrayList<>();
        for (JsonNode student : objectMapper.readTree(body).path("data").path("worksheets")
                .get(0).path("customLearning").path("students")) {
            student.path("sessions").forEach(sessions::add);
        }
        return sessions;
    }

    @Test
    @DisplayName("맞춤 배정 ID로 점수표를 열 수 있다 — 문항별 정오는 거기 있다")
    void getScoreTable_customAssignment_works() throws Exception {
        CustomFixture f = customLearningFixture();
        long questionId = insertQuestion("SHORT_INPUT");
        long answerUnitId = insertAnswerUnit(questionId, "VALUE", "7");
        insertWorksheetItem(f.otherWorksheetId(), questionId, new BigDecimal("10.00"));
        Long assignmentStudentId = jdbcTemplate.queryForObject(
                "SELECT id FROM worksheet_assignment_student WHERE assignment_id = ?",
                Long.class, f.otherAssignmentId());
        insertAnswer(assignmentStudentId, answerUnitId, "VALUE", "GRADED",
                new BigDecimal("10.00"), null);

        mockMvc.perform(get("/api/teacher/grading/" + f.otherAssignmentId())
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 맞춤은 반이 없다.
                .andExpect(jsonPath("$.data.className").doesNotExist())
                .andExpect(jsonPath("$.data.students.length()").value(1))
                .andExpect(jsonPath("$.data.students[0].cells[0].result").value("correct"));
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
    @DisplayName("객관식 채점 상세에는 보기 전체와 정답·선택 보기 ID가 내려간다")
    void getStudentDetail_multipleChoice_includesChoicesAndChoiceIds() throws Exception {
        long questionId = insertQuestion("MULTIPLE_CHOICE");
        long choice1 = insertChoice(questionId, 0, "2");
        long choice2 = insertChoice(questionId, 1, "6");
        insertChoice(questionId, 2, "12");
        // answer_raw 는 1-based 보기 순번이라 "2" 는 displayOrder 1(=choice2)을 가리킨다.
        long answerUnitId = insertAnswerUnit(questionId, "CHOICE", "2");
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        // 학생은 1번 보기를 골랐다 — 오답이다.
        insertChoiceAnswer(assignmentStudentId, answerUnitId, choice1);

        mockMvc.perform(get("/api/teacher/grading/" + assignmentId
                        + "/students/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].choices.length()").value(3))
                .andExpect(jsonPath("$.data.items[0].choices[1].choiceId").value(choice2))
                .andExpect(jsonPath("$.data.items[0].choices[1].text").value("6"))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].correctChoiceId").value(choice2))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].selectedChoiceId").value(choice1))
                .andExpect(jsonPath("$.data.items[0].steps").doesNotExist());
    }

    @Test
    @DisplayName("빈칸형 채점 상세에는 단계 구조가 내려가고 빈칸은 answerUnitId로 칸과 이어진다")
    void getStudentDetail_stepFill_includesSteps() throws Exception {
        long questionId = insertQuestion("STEP_FILL");
        long stepId = insertStepWithSegments(questionId, 0, "1단계", """
                [{"type":"TEXT","value":"84 = "},{"type":"BLANK","unitKey":"B1"}]
                """);
        long unitB1 = insertStepAnswerUnit(questionId, stepId, "B1", 0, "VALUE", "2^2 x 3 x 7");
        long worksheetId = insertWorksheet("GENERAL_LEARNING");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        insertAnswer(assignmentStudentId, unitB1, "VALUE", "GRADED", new BigDecimal("10.00"), null);

        mockMvc.perform(get("/api/teacher/grading/" + assignmentId
                        + "/students/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].steps.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].steps[0].label").value("1단계"))
                .andExpect(jsonPath("$.data.items[0].steps[0].segments[0].type").value("text"))
                .andExpect(jsonPath("$.data.items[0].steps[0].segments[0].value").value("84 = "))
                .andExpect(jsonPath("$.data.items[0].steps[0].segments[1].type").value("blank"))
                .andExpect(jsonPath("$.data.items[0].steps[0].segments[1].answerUnitId").value(unitB1))
                .andExpect(jsonPath("$.data.items[0].choices").doesNotExist());
    }

    @Test
    @DisplayName("서술형은 판정 전에도 기준 목록이 내려가고 satisfied는 미판정이다")
    void getStudentDetail_essayBeforeJudgement_returnsUnjudgedRubric() throws Exception {
        long questionId = insertQuestion("ESSAY");
        long answerUnitId = insertAnswerUnit(questionId, "RUBRIC", null);
        long rubricA = insertRubricItem(questionId, 0, "풀이 과정이 드러나 있음", 4);
        insertRubricItem(questionId, 1, "답이 맞음", 6);
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT");
        insertWorksheetItem(worksheetId, questionId, new BigDecimal("10.00"));
        long assignmentId = insertAssignment(worksheetId);
        long assignmentStudentId = insertAssignmentStudent(assignmentId, "SUBMITTED");
        // 답안은 있으나 루브릭 판정 행이 아직 없다.
        insertAnswer(assignmentStudentId, answerUnitId, "RUBRIC", "NOT_GRADED", null, null);

        mockMvc.perform(get("/api/teacher/grading/" + assignmentId
                        + "/students/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].rubric.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].rubric[0].rubricItemId").value(rubricA))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].rubric[0].description")
                        .value("풀이 과정이 드러나 있음"))
                // 판정 행이 없으므로 "미충족(false)"이 아니라 "미판정(null)"이다.
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].rubric[0].satisfied").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].rubric[1].satisfied").doesNotExist());
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

    private long insertChoice(long questionId, int displayOrder, String content) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_choice(question_id, display_order, content)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, questionId, displayOrder, content);
    }

    private long insertStepWithSegments(long questionId, int displayOrder, String label, String segmentsJson) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_step(question_id, display_order, label, segments)
                VALUES (?, ?, ?, ?::jsonb)
                RETURNING id
                """, Long.class, questionId, displayOrder, label, segmentsJson);
    }

    private long insertStepAnswerUnit(long questionId, long stepId, String unitKey, int displayOrder,
                                      String compareMethod, String answerRaw) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_answer_unit(
                    question_id, step_id, unit_key, display_order,
                    compare_method, answer_raw, answer_normalized)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """, Long.class, questionId, stepId, unitKey, displayOrder,
                compareMethod, answerRaw, answerRaw);
    }

    private long insertChoiceAnswer(long assignmentStudentId, long answerUnitId, long selectedChoiceId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO submission_answer(
                    assignment_student_id, answer_unit_id, input_mode, selected_choice_id,
                    compare_method, grading_status)
                VALUES (?, ?, 'CHOICE', ?, 'CHOICE', 'NOT_GRADED')
                RETURNING id
                """, Long.class, assignmentStudentId, answerUnitId, selectedChoiceId);
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

    // ---------- 맞춤 학습 픽스처 ----------

    /**
     * 맞춤 학습지. {@code parentWorksheetId} 가 차수를 정한다 — 원본 학습지를 가리키면 1차,
     * 1차 맞춤을 가리키면 2차다. {@code sourceAssignmentId} 는 차수와 무관하게 항상 원본 배정이다.
     */
    private long insertCustomWorksheet(String title, String type, long sourceAssignmentId,
                                       long parentWorksheetId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester,
                                      source_assignment_id, parent_worksheet_id, created_at)
                VALUES (?, ?, 'CUSTOM', ?, 1, 'COMMON', ?, ?, now())
                RETURNING id
                """, Long.class, title, type, teacherId, sourceAssignmentId, parentWorksheetId);
    }

    /**
     * 학생 단위 배정. 맞춤은 반이 아니라 학생에게 간다(class_id XOR student_id).
     * 이 경로를 만드는 프로덕션 코드가 없어 테스트는 직접 넣는다.
     */
    private long insertStudentAssignment(long worksheetId, long targetStudentId,
                                         int assignedDaysAgo) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, student_id, assigned_at, due_at)
                VALUES (?, ?, now() - (? * interval '1 day'), now() + interval '7 days')
                RETURNING id
                """, Long.class, worksheetId, targetStudentId, assignedDaysAgo);
    }

    private long insertAssignmentStudent(long assignmentId, long targetStudentId, String status,
                                         int progressCount, boolean submitted, boolean graded) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(
                    assignment_id, student_id, status, progress_count, submitted_at, graded_at)
                VALUES (?, ?, ?, ?, case when ? then now() else null end,
                        case when ? then now() else null end)
                RETURNING id
                """, Long.class, assignmentId, targetStudentId, status, progressCount,
                submitted, graded);
    }

    private long insertStudent(String loginId, String name) {
        long id = insertAccount("STUDENT", loginId, name);
        insertStudentProfile(id, teacherId);
        return id;
    }
}
