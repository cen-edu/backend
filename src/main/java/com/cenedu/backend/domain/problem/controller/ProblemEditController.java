package com.cenedu.backend.domain.problem.controller;

import com.cenedu.backend.domain.problem.dto.request.ProblemEditTurnRequest;
import com.cenedu.backend.domain.problem.dto.response.AuthoringProblemSnapshotResponse;
import com.cenedu.backend.domain.problem.dto.response.AuthoringSessionStatusResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemEditTurnResponse;
import com.cenedu.backend.domain.problem.service.ProblemEditApplicationService;
import com.cenedu.backend.domain.problem.service.ProblemSnapshotQueryService;
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
    private final ProblemSnapshotQueryService snapshotQueryService;

    public ProblemEditController(ProblemEditApplicationService service,
                                 ProblemSnapshotQueryService snapshotQueryService) {
        this.service = service;
        this.snapshotQueryService = snapshotQueryService;
    }

    /** 수정 전후 Session의 current Version과 실패 상태를 조회한다. */
    @GetMapping("/{sessionId}/status")
    public ApiResponse<AuthoringSessionStatusResponse> getStatus(
            @PathVariable long sessionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(snapshotQueryService.getStatus(user.memberId(), sessionId));
    }

    /** 수정 검증을 통과해 현재로 승격된 문제 Snapshot을 조회한다. */
    @GetMapping("/{sessionId}/preview")
    public ApiResponse<AuthoringProblemSnapshotResponse> getPreview(
            @PathVariable long sessionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(snapshotQueryService.getCurrent(user.memberId(), sessionId));
    }

    /** 현재 PASSED Version에 대한 문제 수정 한 턴을 처리한다. */
    @PostMapping("/{sessionId}/edit/turns")
    public ApiResponse<ProblemEditTurnResponse> handleTurn(
            @PathVariable long sessionId, @Valid @RequestBody ProblemEditTurnRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.handleTurn(user.memberId(), sessionId, request));
    }
}
