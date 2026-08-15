package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentComprehensiveAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-comprehensive-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ComprehensiveAssessmentAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private ComprehensiveAssessmentQueryService queryService;

    @Test
    @DisplayName("JWT가 없으면 종합평가 분석 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/item-achievement"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 종합평가 분석 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/item-achievement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("교사 JWT로 종합평가 분석 지표를 조회한다")
    void getsInsights() throws Exception {
        String token = teacherToken();
        when(queryService.getInsights(7L, 101L)).thenReturn(
                new ComprehensiveAssessmentInsightsResponse(
                        List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "comprehensive-assessment-insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionTypeGroups").isArray())
                .andExpect(jsonPath("$.data.difficultyBands").isArray())
                .andExpect(jsonPath("$.data.priorityItems").isArray());
    }

    @Test
    @DisplayName("교사 JWT로 종합평가 문항 성취를 조회한다")
    void getsItemAchievement() throws Exception {
        String token = teacherToken();
        when(queryService.getItemAchievement(7L, 101L)).thenReturn(
                new ComprehensiveAssessmentItemAchievementResponse(
                        List.of(), List.of()));

        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/item-achievement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.students").isArray());
    }

    @Test
    @DisplayName("교사 JWT로 종합평가 시간 득점률 분포를 조회한다")
    void getsScoreTimeDistribution() throws Exception {
        String token = teacherToken();
        when(queryService.getScoreTimeDistribution(7L, 101L)).thenReturn(
                new ScoreTimeDistributionResponse(
                        List.of(), new BigDecimal("70.0"), 120000L));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "score-time-distribution")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentDistribution").isArray())
                .andExpect(jsonPath("$.data.medianScoreRate").value(70.0))
                .andExpect(jsonPath("$.data.medianSolvingDurationMs").value(120000));
    }

    @Test
    @DisplayName("교사 JWT로 종합평가 학생 성취를 조회한다")
    void getsStudentPerformance() throws Exception {
        String token = teacherToken();
        when(queryService.getStudentPerformance(7L, 101L, 11L)).thenReturn(
                new StudentComprehensiveAssessmentPerformanceResponse(
                        List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "comprehensive-assessment-performance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.questionTypeGroups").isArray())
                .andExpect(jsonPath("$.data.difficultyBands").isArray());
    }

    private String teacherToken() {
        return jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
    }
}
