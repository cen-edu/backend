package com.cenedu.backend.domain.problem.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.service.CustomProblemGenerationService;
import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.domain.problem.entity.enums.GenerationJobStatus;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
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
class CustomProblemGenerationControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean CustomProblemGenerationService service;

    @Test
    void teacherCanStartCustomGeneration() throws Exception {
        when(service.start(eq(7L), any(CustomProblemGenerationRequest.class)))
                .thenReturn(new ProblemGenerationStartResponse(91L, GenerationJobStatus.QUEUED, 3));
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();

        mockMvc.perform(post("/api/teacher/custom-problems/generate/async")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"clientRequestId":"00000000-0000-0000-0000-000000000001",
                                 "sourceAssignmentId":120,"studentId":35,
                                 "items":[{"subUnitId":20,"reviewCount":1,"similarCount":1,"advancedCount":1}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(91))
                .andExpect(jsonPath("$.data.totalCount").value(3));
        verify(service).start(eq(7L), any(CustomProblemGenerationRequest.class));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/teacher/custom-problems/generate/async")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentRequestIsForbidden() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();
        mockMvc.perform(post("/api/teacher/custom-problems/generate/async")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }
}
