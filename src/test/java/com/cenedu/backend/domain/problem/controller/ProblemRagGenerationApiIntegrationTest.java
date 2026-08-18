package com.cenedu.backend.domain.problem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** RAG 생성 API의 실제 보안 경계를 PostgreSQL 기반 애플리케이션 컨텍스트에서 검증한다. */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        "app.problem.rag.enabled=true",
        "app.problem.rag.indexing.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ProblemRagGenerationApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void unauthenticatedGenerationRequestIsRejected() throws Exception {
        mockMvc.perform(post("/api/teacher/problems/generate/async")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void studentCannotSubmitTeacherGenerationRequest() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();
        mockMvc.perform(post("/api/teacher/problems/generate/async")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }
}
