package com.cenedu.backend.domain.analysis.controller;

import java.time.Instant;
import java.time.LocalDate;

import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프론트 연동 계층이 읽는 필드가 실제로 나오는지 본다.
 *
 * <p>프론트를 고치지 않기로 했으므로 <b>필드 이름이 계약</b>이다. 이름 하나만 바뀌어도 화면은
 * 오류 없이 빈칸이 된다. 그래서 값이 아니라 경로와 이름을 확인한다.
 *
 * <p>확인 대상은 {@code cen-edu-frontend/src/api/weaknessBackend.js} 가 읽는 필드다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@DisplayName("취약점 분석 API 계약")
class AnalysisApiControllerTest {

    private static final String ASSESSMENT = "A-CONTRACT";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AnalysisAssessmentRepository assessments;

    @Autowired
    private AnalysisAttemptRepository attempts;

    @BeforeEach
    void seed() {
        attempts.deleteAll();
        assessments.deleteAll();

        AnalysisAssessment assessment = AnalysisAssessment.builder()
                .assessmentId(ASSESSMENT).studentId("S-1")
                .assessmentTitle("소인수분해 학습평가")
                .assessmentDate(LocalDate.of(2026, 8, 7))
                .studentName("민수").assessmentType("SUMMATIVE")
                .simulation(true)
                .build();
        assessment.complete();
        assessments.save(assessment);

        attempts.save(attempt("E-1", 1, true, "concept", "소인수분해"));
        attempts.save(attempt("E-2", 2, false, "calculation", "최대공약수"));
    }

    private static AnalysisAttempt attempt(String eventId, int number, boolean correct,
                                           String area, String topic) {
        return AnalysisAttempt.builder()
                .eventId(eventId).assessmentId(ASSESSMENT).studentId("S-1")
                .problemNumber(number).problemId("P-" + number)
                .problemTitle(number + "번 문항").problemText("본문")
                .correct(correct).hintUsed(false)
                .evaluationArea(area).topic(topic).difficultyBand("mid")
                .choicesJson("[\"1\",\"2\",\"3\"]").responseType("choice")
                .studentAnswer("1").correctAnswer("2")
                .stepResponsesJson(
                        "[{\"stepId\":\"S1\",\"stepName\":\"식 세우기\",\"category\":\"c\","
                                + "\"studentAnswer\":\"x=1\",\"correctAnswer\":\"x=2\","
                                + "\"correct\":false}]")
                .occurredAt(Instant.parse("2026-08-07T01:0" + number + ":00Z"))
                .build();
    }

    @Test
    @DisplayName("GET /api/assessments — 학습지 선택 목록")
    void assessmentList() throws Exception {
        mvc.perform(get("/api/assessments"))
                .andExpect(status().isOk())
                // 프론트: value = assessmentId, label = `${assessmentTitle} (${problemCount}문항)`
                .andExpect(jsonPath("$[0].assessmentId").value(ASSESSMENT))
                .andExpect(jsonPath("$[0].assessmentTitle").value("소인수분해 학습평가"))
                .andExpect(jsonPath("$[0].problemCount").value(2));
    }

    @Test
    @DisplayName("GET /{id}/class-summary — 학급 집계")
    void classSummary() throws Exception {
        mvc.perform(get("/api/assessments/{id}/class-summary", ASSESSMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(ASSESSMENT))
                .andExpect(jsonPath("$.assessmentTitle").exists())
                .andExpect(jsonPath("$.assessmentDate").value("2026-08-07"))
                .andExpect(jsonPath("$.assessmentType").value("SUMMATIVE"))
                .andExpect(jsonPath("$.simulation").value(true))
                // 화면의 문항 표
                .andExpect(jsonPath("$.problems[0].problemNumber").value(1))
                .andExpect(jsonPath("$.problems[0].problemId").exists())
                .andExpect(jsonPath("$.problems[0].problemTitle").exists())
                .andExpect(jsonPath("$.problems[0].difficultyBand").value("mid"))
                .andExpect(jsonPath("$.problems[0].evaluationArea").value("concept"))
                .andExpect(jsonPath("$.problems[0].topic").value("소인수분해"))
                // 화면의 학생 목록
                .andExpect(jsonPath("$.students[0].studentId").value("S-1"))
                .andExpect(jsonPath("$.students[0].studentName").value("민수"))
                .andExpect(jsonPath("$.students[0].status").value("priority"));
    }

    @Test
    @DisplayName("GET /{id}/students/{sid}/summary — 학생 상세")
    void studentSummary() throws Exception {
        mvc.perform(get("/api/assessments/{id}/students/{sid}/summary", ASSESSMENT, "S-1"))
                .andExpect(status().isOk())
                // 프론트가 responses 를 만드는 자리
                .andExpect(jsonPath("$.attempts[0].problemNumber").value(1))
                .andExpect(jsonPath("$.attempts[0].correct").value(true))
                .andExpect(jsonPath("$.attempts[0].hintUsed").value(false))
                .andExpect(jsonPath("$.attempts[1].correct").value(false));
    }

    @Test
    @DisplayName("GET /{id}/students/{sid}/review — 학생 답과 정답")
    void studentReview() throws Exception {
        mvc.perform(get("/api/assessments/{id}/students/{sid}/review", ASSESSMENT, "S-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problems[0].problemNumber").value(1))
                .andExpect(jsonPath("$.problems[0].studentAnswer").value("1"))
                .andExpect(jsonPath("$.problems[0].correctAnswer").value("2"))
                .andExpect(jsonPath("$.problems[0].choices[0]").value("1"))
                // 화면은 steps[].stepName 과 studentAnswer 를 읽는다
                .andExpect(jsonPath("$.problems[0].steps[0].stepName").value("식 세우기"))
                .andExpect(jsonPath("$.problems[0].steps[0].studentAnswer").value("x=1"));
    }

    @Test
    @DisplayName("없는 평가는 404 로 답한다")
    void missingAssessmentIsNotFound() throws Exception {
        mvc.perform(get("/api/assessments/{id}/class-summary", "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ASSESSMENT_NOT_FOUND"));
    }
}
