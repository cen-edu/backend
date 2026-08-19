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
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        // .env 의 S3_ENABLED 가 테스트까지 새어들어온다. 켜두면 presign 이 실제 버킷에
        // headObject 를 날려 테스트가 네트워크와 실제 객체 존재에 묶인다.
        "app.storage.s3.enabled=false"
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

    private long teacherId;
    private long studentId;
    private String studentToken;
    private long subUnitId;
    private long choiceQuestionId;
    private long stepQuestionId;
    private long answerRefQuestionId;
    private long assignmentStudentId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "student-api-test-teacher", "테스트교사");
        studentId = insertAccount("STUDENT", "student-api-test-student", "학생1");
        insertStudentProfile(studentId, teacherId);
        studentToken = jwtProvider.issueAccessToken(studentId, UserRole.STUDENT).value();

        subUnitId = insertCurriculumUnit();

        // TEXT + FIGURE + TABLE 세 블록 종류를 한 문항에 다 넣어 stage-1 측정 요구 4번을 겸한다.
        choiceQuestionId = insertQuestion(subUnitId, "MULTIPLE_CHOICE", """
                [
                  {"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"다음 중 소수인 것을 고르시오."},
                  {"blockId":"Q-IMG-1","blockKind":"FIGURE","displayOrder":1,"assetRef":"F1","text":"부채꼴 그림"},
                  {"blockId":"T2","blockKind":"TABLE","displayOrder":2,"text":"","markup":"<table><tr><td>1</td></tr></table>"}
                ]
                """);
        insertAsset(choiceQuestionId, "F1", 0, "questions/30/test_F1.png", 240, 188, "부채꼴 그림");
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

        // 기존 STEP_FILL 픽스처는 1단계·B1 하나라 ANSWER_REF를 표현할 수 없다. 수정하지 않고 따로 만든다.
        // 실측(로컬 DB): ANSWER_REF 1,657개가 전부 자기보다 앞선 display_order의 step을 참조한다.
        answerRefQuestionId = insertQuestion(subUnitId, "STEP_FILL", """
                [{"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"부채꼴의 중심각을 구하시오."}]
                """);
        long refStep1Id = insertStep(answerRefQuestionId, 0, "풀이 과정 1", """
                [{"type":"TEXT","value":"작은 부채꼴의 중심각은"},{"type":"BLANK","unitKey":"B1"}]
                """);
        insertAnswerUnit(answerRefQuestionId, refStep1Id, "B1", 0, "VALUE", "50");
        long refStep2Id = insertStep(answerRefQuestionId, 1, "풀이 과정 2", """
                [{"type":"ANSWER_REF","unitKey":"B1"},
                 {"type":"TEXT","value":"를 이용하면 큰 부채꼴의 중심각은 100°이므로 x = "},
                 {"type":"BLANK","unitKey":"B2"}]
                """);
        insertAnswerUnit(answerRefQuestionId, refStep2Id, "B2", 1, "VALUE", "100");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING", "STANDARD");
        insertWorksheetItem(worksheetId, choiceQuestionId, 1);
        insertWorksheetItem(worksheetId, stepQuestionId, 2);
        insertWorksheetItem(worksheetId, answerRefQuestionId, 3);

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
                // GENERAL_LEARNING → 문항들의 answer_unit 수 합(1+1+2=4), 문항 수(3)가 아니다.
                .andExpect(jsonPath("$.data.assignments[0].totalUnits").value(4))
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
                .andExpect(jsonPath("$.data.items[0].contentBlocks[1].assetRef").value("F1"))
                // 이미지 주소는 블록이 아니라 문항 단위 assets[] 에 있고 assetKey 로 맞춘다.
                .andExpect(jsonPath("$.data.items[0].contentBlocks[1].imageUrl").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].assets[0].assetKey").value("F1"))
                .andExpect(jsonPath("$.data.items[0].assets[0].widthPx").value(240))
                .andExpect(jsonPath("$.data.items[0].assets[0].heightPx").value(188))
                .andExpect(jsonPath("$.data.items[0].assets[0].altText").value("부채꼴 그림"))
                // S3 를 끈 컨텍스트라 url 은 null 이다(빈 부재 시 null 반환 경로).
                .andExpect(jsonPath("$.data.items[0].assets[0].url").doesNotExist())
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
    @DisplayName("ANSWER_REF 세그먼트가 answerRef 토큰과 참조 칸의 answerUnitId로 나가고 value는 없다")
    void getAssignmentDetail_rendersAnswerRefSegment() throws Exception {
        // 2단계 ANSWER_REF(B1)이 가리켜야 하는 값 — 1단계 BLANK(B1)의 answerUnitId와 같아야 한다.
        long b1AnswerUnitId = jdbcTemplate.queryForObject(
                "SELECT id FROM problem_answer_unit WHERE question_id = ? AND unit_key = 'B1'",
                Long.class, answerRefQuestionId);
        long b2AnswerUnitId = jdbcTemplate.queryForObject(
                "SELECT id FROM problem_answer_unit WHERE question_id = ? AND unit_key = 'B2'",
                Long.class, answerRefQuestionId);

        mockMvc.perform(get("/api/student/assignments/" + assignmentStudentId)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[2].format").value("step"))
                .andExpect(jsonPath("$.data.items[2].steps[0].segments[1].type").value("blank"))
                .andExpect(jsonPath("$.data.items[2].steps[0].segments[1].answerUnitId").value(b1AnswerUnitId))
                // 1. 토큰은 소문자 파생(answer_ref)이 아니라 프론트가 비교하는 answerRef다.
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[0].type").value("answerRef"))
                // 2. 앞선 단계 BLANK(B1)와 동일한 answerUnitId — 프론트가 이 ID로 답안 상태를 공유한다.
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[0].answerUnitId").value(b1AnswerUnitId))
                // 3. value는 영구히 null. 서버가 아는 값은 정답뿐이라 채우면 정답이 노출된다.
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[0].value").doesNotExist())
                // 뒤따르는 TEXT/BLANK도 그대로 살아 있어야 한다(세그먼트가 사라지던 증상의 반대편).
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[1].type").value("text"))
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[1].value")
                        .value(org.hamcrest.Matchers.containsString("큰 부채꼴의 중심각은 100°")))
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[2].type").value("blank"))
                .andExpect(jsonPath("$.data.items[2].steps[1].segments[2].answerUnitId").value(b2AnswerUnitId));
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

    @Test
    @DisplayName("concept 지원 문항은 개념 정리를 세 키만 담아 풀이 화면에 내려보낸다")
    void getAssignmentDetail_conceptMode_exposesOnlyThreeKeys() throws Exception {
        // 세 키 외에 내부 출처·품질 등급을 일부러 채운다 — 채워져 있는데도 안 나가는 것이 요지다.
        long questionId = insertQuestionWithLearningGuide(subUnitId, "SHORT_INPUT", """
                [{"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"84를 소인수분해하시오."}]
                """, """
                {"conceptTitle":"소인수분해",
                 "summary":"자연수를 소수의 곱으로 나타내는 것",
                 "keyPoints":["소수는 1과 자기 자신만을 약수로 갖는다","지수로 간단히 표기한다"],
                 "questionSourceRef":"AIHUB30-1234",
                 "source":{"datasets":["AIHUB_110"]},
                 "status":"INTERNAL_APPROVED"}
                """);
        insertAnswerUnit(questionId, null, "MAIN", 0, "VALUE", "2^2 x 3 x 7");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING", "STANDARD");
        insertWorksheetItem(worksheetId, questionId, 1, "CONCEPT_GUIDE");

        mockMvc.perform(get("/api/student/assignments/" + assignToStudent(worksheetId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].supportMode").value("concept"))
                .andExpect(jsonPath("$.data.items[0].concept.title").value("소인수분해"))
                .andExpect(jsonPath("$.data.items[0].concept.summary")
                        .value("자연수를 소수의 곱으로 나타내는 것"))
                .andExpect(jsonPath("$.data.items[0].concept.points[0]")
                        .value("소수는 1과 자기 자신만을 약수로 갖는다"))
                .andExpect(jsonPath("$.data.items[0].concept.points[1]").value("지수로 간단히 표기한다"))
                // 내부 출처·품질 등급·명세에만 있던 키는 DTO에 자리가 없다.
                .andExpect(jsonPath("$.data.items[0].concept.source").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].concept.status").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].concept.questionSourceRef").doesNotExist());
    }

    @Test
    @DisplayName("chat 지원과 지원 없음 문항은 learning_guide가 있어도 concept이 비어 있다")
    void getAssignmentDetail_nonConceptMode_omitsConcept() throws Exception {
        String contentBlocks = """
                [{"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"84를 소인수분해하시오."}]
                """;
        String learningGuide = """
                {"conceptTitle":"소인수분해",
                 "summary":"자연수를 소수의 곱으로 나타내는 것",
                 "keyPoints":["지수로 간단히 표기한다"]}
                """;
        long chatQuestionId = insertQuestionWithLearningGuide(subUnitId, "SHORT_INPUT", contentBlocks, learningGuide);
        insertAnswerUnit(chatQuestionId, null, "MAIN", 0, "VALUE", "2^2 x 3 x 7");
        long plainQuestionId = insertQuestionWithLearningGuide(subUnitId, "SHORT_INPUT", contentBlocks, learningGuide);
        insertAnswerUnit(plainQuestionId, null, "MAIN", 0, "VALUE", "2^2 x 3 x 7");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING", "STANDARD");
        insertWorksheetItem(worksheetId, chatQuestionId, 1, "CHATBOT");
        insertWorksheetItem(worksheetId, plainQuestionId, 2);

        mockMvc.perform(get("/api/student/assignments/" + assignToStudent(worksheetId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].supportMode").value("chat"))
                .andExpect(jsonPath("$.data.items[0].concept").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].supportMode").doesNotExist())
                .andExpect(jsonPath("$.data.items[1].concept").doesNotExist());
    }

    @Test
    @DisplayName("concept 지원인데 learning_guide가 없으면 concept만 비고 문항은 정상 조회된다")
    void getAssignmentDetail_conceptModeWithoutLearningGuide_returnsItemWithoutConcept() throws Exception {
        long questionId = insertQuestion(subUnitId, "SHORT_INPUT", """
                [{"blockId":"T1","blockKind":"TEXT","displayOrder":0,"text":"84를 소인수분해하시오."}]
                """);
        insertAnswerUnit(questionId, null, "MAIN", 0, "VALUE", "2^2 x 3 x 7");

        long worksheetId = insertWorksheet(teacherId, "GENERAL_LEARNING", "STANDARD");
        insertWorksheetItem(worksheetId, questionId, 1, "CONCEPT_GUIDE");

        mockMvc.perform(get("/api/student/assignments/" + assignToStudent(worksheetId))
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].questionId").value(questionId))
                .andExpect(jsonPath("$.data.items[0].supportMode").value("concept"))
                .andExpect(jsonPath("$.data.items[0].format").value("short"))
                .andExpect(jsonPath("$.data.items[0].contentBlocks[0].text").value("84를 소인수분해하시오."))
                .andExpect(jsonPath("$.data.items[0].answerUnits[0].inputMode").value("HANDWRITING"))
                .andExpect(jsonPath("$.data.items[0].concept").doesNotExist());
    }

    /** learning_guide를 채운 문항. 기존 {@link #insertQuestion}은 이 컬럼을 비운다. */
    private long insertQuestionWithLearningGuide(
            long subUnitId, String questionType, String contentBlocks, String learningGuide
    ) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text, learning_guide)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', ?::jsonb, '검색용 원문 — 화면 표시 금지', ?::jsonb)
                RETURNING id
                """, Long.class, subUnitId, questionType, contentBlocks, learningGuide);
    }

    /** 지원 방식을 지정하는 학습지 문항. 3인자 오버로드는 support_mode를 비운다. */
    private void insertWorksheetItem(long worksheetId, long questionId, int displayOrder, String supportMode) {
        jdbcTemplate.update("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, support_mode)
                VALUES (?, ?, ?, ?)
                """, worksheetId, questionId, displayOrder, supportMode);
    }

    /** 학습지를 새 반에 배정하고 학생의 배정 ID를 돌려준다. */
    private long assignToStudent(long worksheetId) {
        long classId = insertClass(teacherId);
        long assignmentId = insertAssignment(worksheetId, classId);
        return insertAssignmentStudent(assignmentId, studentId);
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

    private void insertAsset(long questionId, String assetKey, int displayOrder,
                             String storageKey, int widthPx, int heightPx, String altText) {
        jdbcTemplate.update("""
                INSERT INTO problem_asset(
                    question_id, asset_key, role, display_order,
                    storage_key, width_px, height_px, alt_text)
                VALUES (?, ?, 'FIGURE', ?, ?, ?, ?, ?)
                """, questionId, assetKey, displayOrder, storageKey, widthPx, heightPx, altText);
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
