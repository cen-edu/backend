package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentLearningAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
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
        "app.jwt.secret=cen-edu-learning-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class LearningAssessmentAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private LearningAssessmentQueryService queryService;

    @Test
    @DisplayName("JWT가 없으면 학습평가 분석 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "learning-assessment-insights"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 학습평가 분석 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "learning-assessment-insights")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("교사 JWT로 학습평가 분석 지표를 조회한다")
    void getsInsights() throws Exception {
        when(queryService.getInsights(7L, 101L)).thenReturn(
                new LearningAssessmentInsightsResponse(
                        List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "learning-assessment-insights")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evaluationAreas").isArray())
                .andExpect(jsonPath("$.data.difficultyBands").isArray())
                .andExpect(jsonPath("$.data.priorityItems").isArray());
    }

    @Test
    @DisplayName("교사 JWT로 학습평가 성취를 조회한다")
    void getsAchievement() throws Exception {
        when(queryService.getAchievement(7L, 101L)).thenReturn(
                new LearningAssessmentAchievementResponse(
                        List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/"
                        + "learning-assessment-achievement")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subcategories").isArray())
                .andExpect(jsonPath("$.data.students").isArray())
                .andExpect(jsonPath("$.data.subcategoryRanking").isArray());
    }

    @Test
    @DisplayName("교사 JWT로 학습평가 학생 성취를 조회한다")
    void getsStudentPerformance() throws Exception {
        when(queryService.getStudentPerformance(7L, 101L, 11L)).thenReturn(
                new StudentLearningAssessmentPerformanceResponse(
                        List.of(), List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "learning-assessment-performance")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.evaluationAreas").isArray())
                .andExpect(jsonPath("$.data.difficultyBands").isArray())
                .andExpect(jsonPath("$.data.subcategoryResults").isArray());
    }

    @Test
    @DisplayName("학생 JWT로 학습평가 학생 성취를 호출하면 403을 반환한다")
    void rejectsStudentJwtOnStudentPerformance() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "learning-assessment-performance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    private String teacherToken() {
        return jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
    }
}
