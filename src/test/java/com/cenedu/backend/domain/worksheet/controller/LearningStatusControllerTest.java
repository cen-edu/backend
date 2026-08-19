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
 * 학습 현황 API 검증. 요약 정의·표시 순번처럼 실데이터로 만들 수 없는 경로가 많아
 * 수동 삽입으로 확인한다 — Testcontainers + {@code @Transactional}이라 매 테스트 후 롤백된다.
 *
 * <p>실측상 {@code NOT_SUBMITTED} 확정 행과 {@code "1"}/{@code "2"} 학기 학습지,
 * {@code class_id IS NULL} 배포가 DB에 하나도 없어 여기서만 덮인다.
 */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@Transactional
class LearningStatusControllerTest {

    private static final String LIST = "/api/teacher/learning-status";

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtProvider jwtProvider;
    @Autowired private JdbcTemplate jdbcTemplate;

    private long teacherId;
    private String teacherToken;
    private long classId;
    private long subUnitId;

    @BeforeEach
    void setUp() {
        teacherId = insertAccount("TEACHER", "ls-teacher", "테스트교사");
        teacherToken = jwtProvider.issueAccessToken(teacherId, UserRole.TEACHER).value();
        classId = insertClass("1학년 1반");
        subUnitId = insertCurriculumUnit();
    }

    // ---------- 목록·요약 ----------

    @Test
    @DisplayName("소프트 삭제된 학습지는 목록에 나오지 않는다")
    void getList_softDeletedWorksheet_excluded() throws Exception {
        long liveWorksheetId = insertWorksheet("GENERAL_LEARNING", "살아있는 학습지", "COMMON");
        insertAssignment(liveWorksheetId, classId, 7);
        long deletedWorksheetId = insertWorksheet("GENERAL_LEARNING", "삭제된 학습지", "COMMON");
        insertAssignment(deletedWorksheetId, classId, 7);
        jdbcTemplate.update("UPDATE worksheet SET deleted_at = now() WHERE id = ?", deletedWorksheetId);

        mockMvc.perform(get(LIST).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments.length()").value(1))
                .andExpect(jsonPath("$.data.assignments[0].title").value("살아있는 학습지"));
    }

    @Test
    @DisplayName("마감 전 배포만 있으면 unsubmitted는 0이고 요약 3값 합이 전체 행 수를 넘지 않는다")
    void getList_beforeDue_unsubmittedIsZero() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "마감 전", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        insertAssignmentStudent(assignmentId, student("a"), "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, student("b"), "NOT_STARTED", (short) 1, false, false);
        insertAssignmentStudent(assignmentId, student("c"), "NOT_STARTED", (short) 0, false, false);

        mockMvc.perform(get(LIST).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.assignments").value(1))
                .andExpect(jsonPath("$.data.summary.submitted").value(1))
                .andExpect(jsonPath("$.data.summary.inProgress").value(1))
                // 1 + 1 + 0 = 2 <= 전체 3행. 남는 1명은 not-started 다.
                .andExpect(jsonPath("$.data.summary.unsubmitted").value(0));
    }

    @Test
    @DisplayName("NOT_SUBMITTED로 확정된 행은 unsubmitted에 잡힌다")
    void getList_confirmedNotSubmitted_countedAsUnsubmitted() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "확정 미제출", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        insertAssignmentStudent(assignmentId, student("a"), "NOT_SUBMITTED", (short) 0, false, false);

        mockMvc.perform(get(LIST).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.unsubmitted").value(1))
                .andExpect(jsonPath("$.data.summary.inProgress").value(0));
    }

    @Test
    @DisplayName("마감 후 NOT_STARTED 행은 unsubmitted에 들어가고 inProgress에는 빠진다")
    void getList_pastDueNotStarted_unsubmittedNotInProgress() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "마감 후", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, -1);
        // 풀던 중이었지만 마감이 지났다 — 이중 계상되면 안 된다.
        insertAssignmentStudent(assignmentId, student("a"), "NOT_STARTED", (short) 3, false, false);

        mockMvc.perform(get(LIST).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.unsubmitted").value(1))
                .andExpect(jsonPath("$.data.summary.inProgress").value(0))
                // 마감이 지났으므로 진행 중 배포로도 세지 않는다.
                .andExpect(jsonPath("$.data.summary.assignments").value(0))
                .andExpect(jsonPath("$.data.assignments[0].status").value("completed"));
    }

    @Test
    @DisplayName("semester=first는 DB semester가 \"1\"인 학습지만 반환한다")
    void getList_semesterFilter_convertsToDbValue() throws Exception {
        long firstId = insertWorksheet("GENERAL_LEARNING", "1학기 학습지", "1");
        insertAssignment(firstId, classId, 7);
        long secondId = insertWorksheet("GENERAL_LEARNING", "2학기 학습지", "2");
        insertAssignment(secondId, classId, 7);

        mockMvc.perform(get(LIST).param("semester", "first")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments.length()").value(1))
                .andExpect(jsonPath("$.data.assignments[0].title").value("1학기 학습지"))
                .andExpect(jsonPath("$.data.assignments[0].semester").value("first"));
    }

    @Test
    @DisplayName("남의 학습지 배포는 학생 표에서 404다")
    void getStudents_notOwned_returns404() throws Exception {
        long otherTeacherId = insertAccount("TEACHER", "ls-other-teacher", "다른교사");
        long worksheetId = jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES ('남의 학습지', 'GENERAL_LEARNING', 'STANDARD', ?, 1, 'COMMON', now()) RETURNING id
                """, Long.class, otherTeacherId);
        long assignmentId = insertAssignment(worksheetId, classId, 7);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WORKSHEET_ASSIGNMENT_NOT_FOUND"));
    }

    // ---------- 학생 표 ----------

    @Test
    @DisplayName("일반 학습 학생 행은 grading과 score가 둘 다 null이다")
    void getStudents_practice_gradingAndScoreAreNull() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "일반 학습", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        long studentId = student("a");
        insertAssignmentStudent(assignmentId, studentId, "GRADED", (short) 2, true, true);
        // 채점이 끝나 점수가 있어도 일반 학습이면 내보내지 않는다.
        jdbcTemplate.update("""
                UPDATE worksheet_assignment_student SET total_score = 10.00
                WHERE assignment_id = ? AND student_id = ?
                """, assignmentId, studentId);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("practice"))
                .andExpect(jsonPath("$.data.students[0].grading").doesNotExist())
                .andExpect(jsonPath("$.data.students[0].score").doesNotExist());
    }

    @Test
    @DisplayName("종합평가는 채점 여부에 따라 grading이 pending·done으로 갈리고 score가 나간다")
    void getStudents_assessment_gradingAxis() throws Exception {
        long worksheetId = insertWorksheet("COMPREHENSIVE_ASSESSMENT", "종합 평가", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        long graded = insertAssignmentStudent(assignmentId, student("가"), "GRADED", (short) 2, true, true);
        jdbcTemplate.update("UPDATE worksheet_assignment_student SET total_score = 84.00 WHERE id = ?", graded);
        insertAssignmentStudent(assignmentId, student("나"), "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, student("다"), "NOT_STARTED", (short) 0, false, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].grading").value("done"))
                .andExpect(jsonPath("$.data.students[0].score").value(84.00))
                .andExpect(jsonPath("$.data.students[1].grading").value("pending"))
                .andExpect(jsonPath("$.data.students[1].score").doesNotExist())
                // 미제출은 채점 축 자체가 없다.
                .andExpect(jsonPath("$.data.students[2].grading").doesNotExist());
    }

    @Test
    @DisplayName("마감 전이고 진행 중인 학생은 in-progress다")
    void getStudents_beforeDueWithProgress_isInProgress() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "진행 중", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        insertAssignmentStudent(assignmentId, student("a"), "NOT_STARTED", (short) 3, false, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].status").value("in-progress"))
                .andExpect(jsonPath("$.data.students[0].doneUnits").value(3))
                .andExpect(jsonPath("$.data.students[0].submittedAt").doesNotExist());
    }

    @Test
    @DisplayName("displayNumber는 1..N 연속이고 필터를 걸어도 같은 학생의 값이 유지된다")
    void getStudents_displayNumber_stableAcrossFilters() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "순번 검증", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        // 이름순: 김가 < 박나 < 이다 < 최라 → 1,2,3,4
        insertAssignmentStudent(assignmentId, student("최라"), "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, student("김가"), "NOT_STARTED", (short) 0, false, false);
        insertAssignmentStudent(assignmentId, student("이다"), "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, student("박나"), "NOT_STARTED", (short) 1, false, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].name").value("김가"))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(1))
                .andExpect(jsonPath("$.data.students[1].name").value("박나"))
                .andExpect(jsonPath("$.data.students[1].displayNumber").value(2))
                .andExpect(jsonPath("$.data.students[2].name").value("이다"))
                .andExpect(jsonPath("$.data.students[2].displayNumber").value(3))
                .andExpect(jsonPath("$.data.students[3].name").value("최라"))
                .andExpect(jsonPath("$.data.students[3].displayNumber").value(4));

        // 필터를 걸어도 1부터 다시 매기지 않는다 — 이다는 여전히 3번이다.
        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .param("status", "submitted")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.length()").value(2))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(3))
                .andExpect(jsonPath("$.data.students[1].displayNumber").value(4));

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .param("status", "in-progress")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.length()").value(1))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(2));

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .param("status", "not-started")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.length()").value(1))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(1));

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .param("status", "not-submitted")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 2 + 1 + 1 + 0 = 4 = 필터 없는 결과
                .andExpect(jsonPath("$.data.students.length()").value(0));
    }

    @Test
    @DisplayName("동명이인은 studentId 오름차순으로 연속 번호를 받는다")
    void getStudents_sameName_ordersByStudentId() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "동명이인", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        long first = student("김민서");
        long second = student("김민서");
        // 삽입 순서를 뒤집어 넣어도 studentId 순으로 나와야 한다.
        insertAssignmentStudent(assignmentId, second, "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, first, "SUBMITTED", (short) 2, true, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].studentId").value(first))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(1))
                .andExpect(jsonPath("$.data.students[1].studentId").value(second))
                .andExpect(jsonPath("$.data.students[1].displayNumber").value(2));
    }

    @Test
    @DisplayName("담당이 바뀌어 이름을 못 찾는 학생은 맨 뒤로 가고 번호는 그대로 받는다")
    void getStudents_unknownName_goesLastButKeepsNumber() throws Exception {
        long otherTeacherId = insertAccount("TEACHER", "ls-name-other", "다른교사");
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "이름 없음", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);

        long unknown = insertAccount("STUDENT", "ls-unknown", "가나다");
        insertStudentProfile(unknown, otherTeacherId);
        insertAssignmentStudent(assignmentId, unknown, "SUBMITTED", (short) 2, true, false);
        insertAssignmentStudent(assignmentId, student("홍길동"), "SUBMITTED", (short) 2, true, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 이름이 "가나다"라 사전순이면 앞이지만, 조회되지 않으므로 뒤로 간다.
                .andExpect(jsonPath("$.data.students[0].name").value("홍길동"))
                .andExpect(jsonPath("$.data.students[0].displayNumber").value(1))
                .andExpect(jsonPath("$.data.students[1].name").doesNotExist())
                .andExpect(jsonPath("$.data.students[1].displayNumber").value(2));
    }

    @Test
    @DisplayName("배포 후 반에 추가된 학생은 표에 나오지 않는다")
    void getStudents_enrolledAfterAssignment_notListed() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "배포 시점 명단", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        long assigned = student("배정된학생");
        insertAssignmentStudent(assignmentId, assigned, "SUBMITTED", (short) 2, true, false);

        // 반에는 등록돼 있지만(student 헬퍼가 넣는다) 이 배포에는 행이 없다 —
        // 명단은 worksheet_assignment_student 가 정본이다.
        student("전학온학생");

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.length()").value(1))
                .andExpect(jsonPath("$.data.students[0].studentId").value(assigned));
    }

    @Test
    @DisplayName("class_id가 NULL인 맞춤 배포는 className이 null이다")
    void getStudents_customAssignment_classNameIsNull() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "맞춤 학습", "COMMON");
        long studentId = student("맞춤학생");
        long assignmentId = jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, student_id, assigned_at, due_at)
                VALUES (?, ?, now(), now() + interval '7 days') RETURNING id
                """, Long.class, worksheetId, studentId);
        insertAssignmentStudent(assignmentId, studentId, "NOT_STARTED", (short) 0, false, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.className").doesNotExist())
                .andExpect(jsonPath("$.data.students[0].name").value("맞춤학생"));
    }

    @Test
    @DisplayName("일반 학습의 totalUnits는 문항 수가 아니라 채점 칸 수 합이다")
    void getStudents_practiceTotalUnits_countsAnswerUnits() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "칸 수 합", "COMMON");
        long questionId = insertQuestion("STEP_FILL");
        insertAnswerUnit(questionId, "B1");
        insertAnswerUnit(questionId, "B2");
        insertAnswerUnit(questionId, "B3");
        insertWorksheetItem(worksheetId, questionId, 1);
        long assignmentId = insertAssignment(worksheetId, classId, 7);
        insertAssignmentStudent(assignmentId, student("a"), "NOT_STARTED", (short) 0, false, false);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                // 문항은 1개지만 칸이 3개다.
                .andExpect(jsonPath("$.data.totalUnits").value(3));
    }

    @Test
    @DisplayName("status 파라미터에 없는 값을 주면 빈 목록이 아니라 400이다")
    void getStudents_invalidStatus_returns400() throws Exception {
        long worksheetId = insertWorksheet("GENERAL_LEARNING", "잘못된 필터", "COMMON");
        long assignmentId = insertAssignment(worksheetId, classId, 7);

        mockMvc.perform(get(LIST + "/" + assignmentId + "/students")
                        .param("status", "unsubmitted")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    // ---------- 픽스처 ----------

    private long student(String name) {
        long studentId = insertAccount("STUDENT", "ls-s-" + name + "-" + System.nanoTime(), name);
        insertStudentProfile(studentId, teacherId);
        jdbcTemplate.update(
                "INSERT INTO member_class_enrollment(class_id, student_id) VALUES (?, ?)", classId, studentId);
        return studentId;
    }

    private long insertAccount(String role, String loginId, String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_account(role, login_id, password_hash, name)
                VALUES (?, ?, 'test-password-hash', ?) RETURNING id
                """, Long.class, role, loginId, name);
    }

    private void insertStudentProfile(long studentId, long ownerTeacherId) {
        jdbcTemplate.update("""
                INSERT INTO member_student_profile(user_id, registration_year, grade, owner_teacher_id)
                VALUES (?, 2026, 1, ?)
                """, studentId, ownerTeacherId);
    }

    private long insertClass(String name) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO member_school_class(academic_year, grade, name, homeroom_teacher_id, display_order)
                VALUES (2026, 1, ?, ?, 0) RETURNING id
                """, Long.class, name, teacherId);
    }

    private long insertCurriculumUnit() {
        long major = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (null, 'ls-major', 'MAJOR_UNIT', '대단원', 1, 0) RETURNING id
                """, Long.class);
        long middle = jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'ls-middle', 'MIDDLE_UNIT', '중단원', 1, 0) RETURNING id
                """, Long.class, major);
        return jdbcTemplate.queryForObject("""
                INSERT INTO curriculum_unit(parent_id, external_key, unit_level, name, grade, display_order)
                VALUES (?, 'ls-sub', 'SUB_UNIT', '소단원', 1, 0) RETURNING id
                """, Long.class, middle);
    }

    private long insertQuestion(String questionType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO problem_question(
                    source_type, sub_unit_id, difficulty, question_type,
                    presentation, content_blocks, prompt_text, explanation)
                VALUES ('IMPORTED', ?, 1, ?, 'TEXT_ONLY', '[]'::jsonb, '원문', '해설') RETURNING id
                """, Long.class, subUnitId, questionType);
    }

    private void insertAnswerUnit(long questionId, String unitKey) {
        jdbcTemplate.update("""
                INSERT INTO problem_answer_unit(question_id, unit_key, display_order, compare_method, answer_raw)
                VALUES (?, ?, 0, 'VALUE', '1')
                """, questionId, unitKey);
    }

    private void insertWorksheetItem(long worksheetId, long questionId, int displayOrder) {
        jdbcTemplate.update("""
                INSERT INTO worksheet_item(worksheet_id, question_id, display_order, max_score)
                VALUES (?, ?, ?, null)
                """, worksheetId, questionId, displayOrder);
    }

    private long insertWorksheet(String type, String title, String semester) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet(title, type, origin, owner_teacher_id, grade, semester, created_at)
                VALUES (?, ?, 'STANDARD', ?, 1, ?, now()) RETURNING id
                """, Long.class, title, type, teacherId, semester);
    }

    /** dueInDays가 음수면 이미 마감된 배포다. */
    private long insertAssignment(long worksheetId, long targetClassId, int dueInDays) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment(worksheet_id, class_id, assigned_at, due_at)
                VALUES (?, ?, now() - interval '1 day', now() + (? * interval '1 day')) RETURNING id
                """, Long.class, worksheetId, targetClassId, dueInDays);
    }

    private long insertAssignmentStudent(long assignmentId, long studentId, String status,
                                         short progressCount, boolean submitted, boolean graded) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO worksheet_assignment_student(
                    assignment_id, student_id, status, progress_count, submitted_at, graded_at)
                VALUES (?, ?, ?, ?, case when ? then now() else null end,
                        case when ? then now() else null end)
                RETURNING id
                """, Long.class, assignmentId, studentId, status, progressCount, submitted, graded);
    }
}
