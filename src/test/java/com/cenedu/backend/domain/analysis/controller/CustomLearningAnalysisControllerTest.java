package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.service.CustomLearningQueryService;
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
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class CustomLearningAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private CustomLearningQueryService queryService;

    @Test
    @DisplayName("교사 JWT로 학생의 맞춤 학습 회차를 조회한다")
    void getsCustomLearningSessions() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(queryService.getSessions(7L, 101L, 11L)).thenReturn(
                new CustomLearningSessionListResponse(List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "custom-learning-sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sessions").isArray());
    }

    @Test
    @DisplayName("JWT가 없으면 맞춤 학습 결과 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "custom-learning-sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 맞춤 학습 결과 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students/11/"
                        + "custom-learning-sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
