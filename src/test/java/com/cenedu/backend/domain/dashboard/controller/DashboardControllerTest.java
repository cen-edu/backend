package com.cenedu.backend.domain.dashboard.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.dashboard.dto.request.DashboardAssignmentListRequest;
import com.cenedu.backend.domain.dashboard.dto.request.DashboardClassRequest;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardAssignmentListResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardStudentProgressResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.cenedu.backend.domain.dashboard.service.DashboardQueryService;
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
        "app.jwt.secret=cen-edu-dashboard-controller-test-secret-32-bytes",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private DashboardQueryService queryService;

    @Test
    @DisplayName("JWT가 없으면 대시보드 API는 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/teacher/dashboard/summary")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT로 대시보드 API를 호출하면 403을 반환한다")
    void rejectsStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(get("/api/teacher/dashboard/summary")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("교사 JWT로 대시보드 요약을 조회한다")
    void getsSummary() throws Exception {
        when(queryService.getSummary(7L, new DashboardClassRequest(3L, 2)))
                .thenReturn(new DashboardSummaryResponse(
                        OffsetDateTime.now(),
                        new DashboardSummaryResponse.LearningSummary(
                                6, 4, new BigDecimal("74.0"), 8, 10, 1, 1,
                                new BigDecimal("60.0")),
                        new DashboardSummaryResponse.StudentStatusCounts(1, 1, 6, 0)));

        mockMvc.perform(get("/api/teacher/dashboard/summary")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.assignmentCount").value(6))
                .andExpect(jsonPath("$.data.studentStatusCounts.good").value(6));
    }

    @Test
    @DisplayName("교사 JWT로 학생별 학습 현황을 조회한다")
    void getsStudentProgress() throws Exception {
        when(queryService.getStudentProgress(7L, new DashboardClassRequest(3L, 2)))
                .thenReturn(new DashboardStudentProgressResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/teacher/dashboard/student-progress")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.worksheetColumns").isArray())
                .andExpect(jsonPath("$.data.students").isArray());
    }

    @Test
    @DisplayName("학습지 목록의 page와 size를 생략하면 기본값을 사용한다")
    void getsAssignmentsWithPaginationDefaults() throws Exception {
        DashboardAssignmentListRequest request =
                new DashboardAssignmentListRequest(3L, 2, null, null);
        when(queryService.getAssignments(7L, request)).thenReturn(
                new DashboardAssignmentListResponse(
                        List.of(),
                        new DashboardAssignmentListResponse.PageInfo(0, 0, 0)));

        mockMvc.perform(get("/api/teacher/dashboard/assignments")
                        .queryParam("classId", "3")
                        .queryParam("semester", "2")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignments").isArray())
                .andExpect(jsonPath("$.data.page.number").value(0));
    }

    @Test
    @DisplayName("학기 범위를 벗어나면 400을 반환한다")
    void validatesSemester() throws Exception {
        mockMvc.perform(get("/api/teacher/dashboard/summary")
                        .queryParam("classId", "3")
                        .queryParam("semester", "3")
                        .header("Authorization", "Bearer " + teacherToken()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
    }

    private String teacherToken() {
        return jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
    }
}
