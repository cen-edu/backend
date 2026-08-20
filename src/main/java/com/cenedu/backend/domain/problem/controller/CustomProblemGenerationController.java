package com.cenedu.backend.domain.problem.controller;

import com.cenedu.backend.domain.problem.dto.request.CustomProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.service.CustomProblemGenerationService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 맞춤 문제 생성 Job을 접수하는 교사 API. */
@RestController
@RequestMapping("/api/teacher/custom-problems")
public class CustomProblemGenerationController {
    private final CustomProblemGenerationService service;

    public CustomProblemGenerationController(CustomProblemGenerationService service) {
        this.service = service;
    }

    /** 최신 취약점 제안으로 맞춤 문제 생성 Job을 비동기 접수한다. */
    @PostMapping("/generate/async")
    public ApiResponse<ProblemGenerationStartResponse> generateAsync(
            @Valid @RequestBody CustomProblemGenerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(service.start(user.memberId(), request));
    }
}
