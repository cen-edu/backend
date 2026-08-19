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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;

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
    @Operation(summary = "문제 수정 대화 한 턴", description = "교사 확인 전에는 semantic patch를 미리 보여주고, 확인 후에는 answer-free 실행 preview를 반환합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 대화 또는 실행 preview 반환", content = @Content(examples = {
                    @ExampleObject(name = "parametricPreview", value = "{\"success\":true,\"data\":{\"action\":\"CONFIRM_EXECUTION\",\"preview\":{\"mode\":\"PARAMETRIC_PATCH\",\"impactedAreas\":[\"STEM\",\"ANSWERS\",\"EXPLANATION\"]}}}"),
                    @ExampleObject(name = "presentationalPatch", value = "{\"success\":true,\"data\":{\"action\":\"REQUEST_CONFIRMATION\",\"semanticPatch\":{\"mode\":\"PRESENTATIONAL_PATCH\"}}}"),
                    @ExampleObject(name = "structuralRegeneration", value = "{\"success\":true,\"data\":{\"action\":\"REQUEST_CONFIRMATION\",\"semanticPatch\":{\"mode\":\"STRUCTURAL_REGENERATION\",\"operations\":[]}}}"),
                    @ExampleObject(name = "restore", value = "{\"success\":true,\"data\":{\"action\":\"CONFIRM_EXECUTION\",\"preview\":{\"mode\":\"RESTORE\"}}}"),
                    @ExampleObject(name = "legacyFallback", value = "{\"success\":true,\"data\":{\"preview\":{\"legacyFallback\":true}}}")
            })),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "거부된 semantic 수정 또는 curriculum 범위 밖 요청"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "stale base 또는 semantic model 미지원"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "semantic/diagram 검증 실패")
    })
    @PostMapping("/{sessionId}/edit/turns")
    public ApiResponse<ProblemEditTurnResponse> handleTurn(
            @PathVariable long sessionId, @Valid @RequestBody ProblemEditTurnRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.handleTurn(user.memberId(), sessionId, request));
    }
}
