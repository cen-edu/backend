package com.cenedu.backend.domain.analysis.controller;

import java.time.Instant;
import java.time.LocalDate;

import com.cenedu.backend.domain.analysis.entity.AnalysisAssessment;
import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.repository.AnalysisAssessmentRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisAttemptRepository;
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
 * worksheet 계약을 고정한다.
 *
 * <p>{@link AnalysisApiControllerTest} 와 마찬가지로 값이 아니라 필드 이름을 본다. 다만 여기서는
 * {@code attempted} 를 함께 확인한다. 이 값이 잘못되면 화면 숫자가 틀리는 게 아니라
 * <b>그럴듯하게 부풀려진다</b> — 앞에서 막힌 학생일수록 달성률이 높게 나온다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@DisplayName("worksheet 계약")
class WeaknessAnalysisControllerTest {

    private static final String ASSESSMENT = "A-WORKSHEET";

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

        assessments.save(AnalysisAssessment.builder()
                .assessmentId(ASSESSMENT).studentId("S-1")
                .assessmentTitle("소인수분해 종합평가")
                .assessmentDate(LocalDate.of(2026, 8, 3))
                .studentName("민수").assessmentType("SUMMATIVE")
                .simulation(true)
                .build());

        // 1번 구간은 맞히고 2번에서 막힌 뒤 3번을 비웠다.
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-1").assessmentId(ASSESSMENT).studentId("S-1")
                .problemNumber(1).problemId("P-1").problemTitle("소인수분해")
                .problemText("문항 본문").conceptId("PRIME").topic("소인수분해")
                .correct(false).hintUsed(false)
                .evaluationArea("calculation").difficultyBand("high")
                .responseType("SHORT").studentAnswer("12").correctAnswer("2^2x3")
                .stepResponsesJson("""
                        [{"stepId":"S1","stepName":"소인수 찾기","studentAnswer":"2,3","correct":true},
                         {"stepId":"S2","stepName":"지수 쓰기","studentAnswer":"","correct":false},
                         {"stepId":"S3","stepName":"정리","studentAnswer":"","correct":false}]
                        """)
                .occurredAt(Instant.parse("2026-08-03T01:00:00Z"))
                .build());
    }

    @Test
    @DisplayName("GET /api/weakness-analysis/worksheets — 학습지 목록")
    void worksheetList() throws Exception {
        mvc.perform(get("/api/weakness-analysis/worksheets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ASSESSMENT))
                .andExpect(jsonPath("$[0].title").value("소인수분해 종합평가"))
                .andExpect(jsonPath("$[0].type").value("assessment"))
                .andExpect(jsonPath("$[0].term").value("second"))
                .andExpect(jsonPath("$[0].gradeId").exists())
                .andExpect(jsonPath("$[0].className").exists());
    }

    @Test
    @DisplayName("GET /worksheets/{id} — 화면이 통째로 읽는 구조")
    void worksheetDetail() throws Exception {
        mvc.perform(get("/api/weakness-analysis/worksheets/{id}", ASSESSMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ASSESSMENT))
                .andExpect(jsonPath("$.origin").value("manual"))
                .andExpect(jsonPath("$.concepts[0].label").value("소인수분해"))
                .andExpect(jsonPath("$.questions[0].no").value(1))
                .andExpect(jsonPath("$.questions[0].difficulty").value("high"))
                .andExpect(jsonPath("$.questions[0].area").value("calculation"))
                .andExpect(jsonPath("$.questions[0].format").value("short"))
                .andExpect(jsonPath("$.questions[0].maxScore").value(1))
                .andExpect(jsonPath("$.students[0].name").value("민수"))
                .andExpect(jsonPath("$.students[0].responses[0].score").value(0));
    }

    @Test
    @DisplayName("막힌 구간까지는 도달로 세고 그 뒤는 세지 않는다")
    void attemptedCountsUpToTheBlockingStep() throws Exception {
        mvc.perform(get("/api/weakness-analysis/worksheets/{id}", ASSESSMENT))
                .andExpect(status().isOk())
                // 답을 쓴 1번 구간은 도달
                .andExpect(jsonPath("$.students[0].responses[0].steps[0].attempted").value(true))
                // 답은 비었지만 처음 틀린 구간이므로 도달. 여기서 막힌 것이다.
                .andExpect(jsonPath("$.students[0].responses[0].steps[1].attempted").value(true))
                // 그 뒤는 손대지 않았다. 여기를 도달로 세면 달성률이 부풀려진다.
                .andExpect(jsonPath("$.students[0].responses[0].steps[2].attempted").value(false));
    }

    @Test
    @DisplayName("낸 문항을 다 풀지 않은 학생은 정답률로 상태를 정하지 않는다")
    void partialSubmissionIsInsufficient() throws Exception {
        assessments.save(AnalysisAssessment.builder()
                .assessmentId(ASSESSMENT).studentId("S-2")
                .assessmentTitle("소인수분해 종합평가")
                .assessmentDate(LocalDate.of(2026, 8, 3))
                .studentName("서연").assessmentType("SUMMATIVE")
                .simulation(true)
                .build());
        // S-1 이 푼 1번을 풀지 않고 2번만 풀었다. 문항 수가 2개로 늘어 S-2 는 절반만 낸 셈이다.
        attempts.save(AnalysisAttempt.builder()
                .eventId("E-2").assessmentId(ASSESSMENT).studentId("S-2")
                .problemNumber(2).problemId("P-2").problemTitle("최대공약수")
                .conceptId("GCD").correct(true)
                .occurredAt(Instant.parse("2026-08-03T02:00:00Z"))
                .build());

        mvc.perform(get("/api/weakness-analysis/worksheets/{id}", ASSESSMENT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.students[1].name").value("서연"))
                .andExpect(jsonPath("$.students[1].status").value("insufficient"))
                .andExpect(jsonPath("$.students[1].nextAction").value("추가 응답 확인"));
    }

    @Test
    @DisplayName("없는 학습지는 404 로 답한다")
    void missingWorksheetIsNotFound() throws Exception {
        mvc.perform(get("/api/weakness-analysis/worksheets/{id}", "NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("ASSESSMENT_NOT_FOUND"));
    }
}
