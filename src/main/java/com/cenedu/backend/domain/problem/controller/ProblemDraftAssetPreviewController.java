package com.cenedu.backend.domain.problem.controller;

import com.cenedu.backend.domain.problem.dto.response.DraftAssetPreviewResponse;
import com.cenedu.backend.domain.problem.service.ProblemDraftAssetPreviewService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/** 교사가 문제 확정 전에 draft SVG를 확인하는 API다. */
@RestController
@RequestMapping("/api/teacher/problems/authoring-sessions")
public class ProblemDraftAssetPreviewController {
    private final ProblemDraftAssetPreviewService service;

    public ProblemDraftAssetPreviewController(ProblemDraftAssetPreviewService service) {
        this.service = service;
    }

    /** 소유권과 checksum을 확인한 draft asset을 data URL로 반환한다. */
    @GetMapping("/{sessionId}/versions/{versionId}/assets/{assetKey}/preview")
    public ResponseEntity<ApiResponse<DraftAssetPreviewResponse>> preview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long sessionId, @PathVariable long versionId, @PathVariable String assetKey) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ApiResponse.success(service.preview(user.memberId(), sessionId, versionId, assetKey)));
    }
}
