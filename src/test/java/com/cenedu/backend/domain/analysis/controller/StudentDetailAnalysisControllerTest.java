package com.cenedu.backend.domain.analysis.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.service.StudentDetailQueryService;
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
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class StudentDetailAnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private StudentDetailQueryService queryService;

    @Test
    @DisplayName("JWT가 없으면 학생 상세 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/students/11/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 학생 상세 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/students/11/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("교사 JWT로 학생 분석 요약을 조회한다")
    void getsSummary() throws Exception {
        when(queryService.getSummary(7L, 101L, 11L)).thenReturn(
                new StudentAnalysisSummaryResponse(
                        11L, "김민수", "1반", "학습평가",
                        WorksheetType.GENERAL_LEARNING,
                        AnalysisStatus.INTENSIVE, 4, 4, 1,
                        new BigDecimal("25.0"), new BigDecimal("64.3"),
                        null, null, List.of()));

        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/students/11/summary")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studentId").value(11))
                .andExpect(jsonPath("$.data.performanceRate").value(25.0))
                .andExpect(jsonPath("$.data.classPerformanceRate").value(64.3))
                .andExpect(jsonPath("$.data.weaknessSubcategories").isArray());
    }

    @Test
    @DisplayName("교사 JWT로 학생 문항별 결과를 조회한다")
    void getsItems() throws Exception {
        when(queryService.getItems(7L, 101L, 11L)).thenReturn(
                new StudentItemResultListResponse(1001L, List.of()));

        mockMvc.perform(get(
                        "/api/teacher/analysis/assignments/101/students/11/items")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assignmentStudentId").value(1001))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    private String teacherToken() {
        return jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
    }
}
