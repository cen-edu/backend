package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisAssignmentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
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
        "app.jwt.secret=cen-edu-analysis-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class AnalysisClassControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private AnalysisClassQueryService queryService;

    @Test
    @DisplayName("JWT가 없으면 분석 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/teacher/analysis/assignments")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 교사 분석 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/teacher/analysis/assignments")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("교사 JWT로 분석 학습지 목록을 조회한다")
    void getsAssignmentsWithTeacherJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(queryService.getAssignments(any(Long.class), any())).thenReturn(
                new AnalysisAssignmentListResponse(List.of(
                        new AnalysisAssignmentListResponse.AssignmentOption(
                                101L,
                                "2단원 종합평가",
                                WorksheetType.COMPREHENSIVE_ASSESSMENT,
                                true))));

        mockMvc.perform(get("/api/teacher/analysis/assignments")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignments[0].assignmentId").value(101))
                .andExpect(jsonPath("$.data.assignments[0].analysisAvailable").value(true));
    }

    @Test
    @DisplayName("교사 JWT로 분석 대상 학생 목록을 조회한다")
    void getsStudentsWithTeacherJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(queryService.getStudents(7L, 101L)).thenReturn(
                new AnalysisStudentListResponse(List.of()));

        mockMvc.perform(get("/api/teacher/analysis/assignments/101/students")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students").isArray());
    }
}
