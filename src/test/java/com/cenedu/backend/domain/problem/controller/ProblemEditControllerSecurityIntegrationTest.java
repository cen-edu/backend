package com.cenedu.backend.domain.problem.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cenedu.backend.global.common.enums.UserRole;
import com.cenedu.backend.global.security.JwtProvider;
import com.cenedu.backend.support.PostgresTestcontainer;
import com.cenedu.backend.domain.problem.dto.response.ProblemEditTurnResponse;
import com.cenedu.backend.domain.problem.authoring.edit.EditConversationAction;
import com.cenedu.backend.domain.problem.service.ProblemEditApplicationService;
import com.cenedu.backend.domain.problem.service.ProblemSnapshotQueryService;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 문제 수정 turn endpoint의 인증·역할 경계를 실제 HTTP 요청으로 검증한다. */
@SpringBootTest(properties = {
        "app.jwt.secret=cen-edu-test-jwt-secret-32-bytes-minimum",
        "app.jwt.access-token-expiration=1h"
})
@AutoConfigureMockMvc
@Import(PostgresTestcontainer.class)
class ProblemEditControllerSecurityIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean ProblemEditApplicationService service;
    @MockitoBean ProblemSnapshotQueryService snapshotQueryService;

    @Test
    void unauthenticatedTurn_isRejectedBeforeRequestBinding() throws Exception {
        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void studentTurn_isForbiddenByTeacherRoute() throws Exception {
        String token = jwtProvider.issueAccessToken(11L, UserRole.STUDENT).value();
        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void teacherTurn_returnsApiEnvelopeAndKeepsPreviewNullBeforeConfirmation() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(service.handleTurn(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ProblemEditTurnResponse(EditConversationAction.REQUEST_CONFIRMATION,
                        java.util.List.of(), "확인해 주세요."));

        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"userInput\":\"반지름을 5로 바꿔줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.action").value("REQUEST_CONFIRMATION"))
                .andExpect(jsonPath("$.data.assistantMessage").value("확인해 주세요."))
                .andExpect(jsonPath("$.data.preview").doesNotExist());
        verify(service).handleTurn(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void teacherTurn_mapsSemanticErrorsToDocumentedHttpStatuses() throws Exception {
        String token = jwtProvider.issueAccessToken(7L, UserRole.TEACHER).value();
        when(service.handleTurn(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_SEMANTIC_EDIT_REJECTED));
        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"userInput\":\"범위를 벗어난 수정\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));

        when(service.handleTurn(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE));
        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"userInput\":\"오래된 기준\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));

        when(service.handleTurn(eq(7L), eq(31L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID));
        mockMvc.perform(post("/api/teacher/problems/authoring-sessions/31/edit/turns")
                        .header("Authorization", "Bearer " + token).contentType("application/json")
                        .content("{\"userInput\":\"검증 실패\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false));
    }
}
