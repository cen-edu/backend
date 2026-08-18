package com.cenedu.backend.domain.problem.controller;

import com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemEditTurnResponse;
import com.cenedu.backend.domain.problem.service.ProblemEditApplicationService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 교사 문제 수정 대화를 AgentDispatcher 경로로 연결한다. */
@RestController
@RequestMapping("/api/teacher/problems/authoring-sessions")
public class ProblemEditController {
    private final ProblemEditApplicationService service;

    public ProblemEditController(ProblemEditApplicationService service) {
        this.service = service;
    }

    /** 현재 PASSED Version에 대한 문제 수정 한 턴을 처리한다. */
    @PostMapping("/{sessionId}/edit/turns")
    public ApiResponse<ProblemEditTurnResponse> handleTurn(
            @PathVariable long sessionId, @Valid @RequestBody ProblemEditTurnRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.handleTurn(user.memberId(), sessionId, request));
    }
}
