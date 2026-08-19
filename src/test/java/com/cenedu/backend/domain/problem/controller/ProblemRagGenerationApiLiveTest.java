package com.cenedu.backend.domain.problem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** OpenAI 키가 명시적으로 제공된 환경에서만 실행하는 RAG API provider gate다. */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h",
        "app.problem.rag.enabled=true",
        "app.problem.rag.indexing.enabled=false"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class ProblemRagGenerationApiLiveTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;

    @Test
    void providerGateCanReachTeacherEndpoint() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        mockMvc.perform(post("/api/teacher/problems/generate/async")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
