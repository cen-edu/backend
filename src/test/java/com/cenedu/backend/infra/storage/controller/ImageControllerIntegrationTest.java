package com.cenedu.backend.infra.storage.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.domain.submission.service.SubmissionImageService;
import com.cenedu.backend.domain.problem.service.ProblemImageService;
import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.storage.s3.enabled=true",
        "app.storage.s3.region=ap-northeast-2",
        "app.storage.s3.problem-bucket=problem-bucket",
        "app.storage.s3.answer-bucket=answer-bucket",
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ImageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @MockitoBean
    private SubmissionImageService submissionImageService;

    @MockitoBean
    private ProblemImageService problemImageService;

    @Test
    @DisplayName("JWT가 없는 이미지 요청은 401을 반환한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/api/images/answers/1001/answer-units/501"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("학생 JWT와 multipart 이미지로 업로드하면 204를 반환한다")
    void uploadsWithStudentJwt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "answer.png", "image/png", new byte[]{1, 2, 3});
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();

        mockMvc.perform(multipart(
                        "/api/images/answers/1001/answer-units/501")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(submissionImageService).upload(
                11L, UserRole.STUDENT, 1001L, 501L, file);
    }

    @Test
    @DisplayName("교사 JWT로 답안 이미지를 조회하면 URL을 공통 응답에 담는다")
    void getsUrlWithTeacherJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(submissionImageService.createGetUrl(
                7L, UserRole.TEACHER, 1001L, 501L))
                .thenReturn("https://example.com/image");

        mockMvc.perform(get("/api/images/answers/1001/answer-units/501")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://example.com/image"));
    }

    @Test
    @DisplayName("교사 JWT와 multipart 이미지로 문항 대표 이미지를 업로드한다")
    void uploadsProblemImageWithTeacherJwt() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "problem.png", "image/png", new byte[]{1, 2, 3});
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();

        mockMvc.perform(multipart("/api/images/problems/1")
                        .file(file)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        verify(problemImageService).upload(7L, UserRole.TEACHER, 1L, file);
    }

    @Test
    @DisplayName("학생 JWT로도 문항 이미지 조회 URL을 받을 수 있다")
    void getsProblemImageUrlWithStudentJwt() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();
        when(problemImageService.createGetUrl(1L))
                .thenReturn("https://example.com/problem");

        mockMvc.perform(get("/api/images/problems/1")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.url").value("https://example.com/problem"));
    }
}
