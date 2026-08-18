package com.cenedu.backend.domain.problem.controller;

import java.util.List;

import com.cenedu.backend.domain.problem.dto.request.AsyncProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.request.ProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationJobStatusResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.service.ProblemAsyncGenerationService;
import com.cenedu.backend.domain.problem.service.ProblemGenerationService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teacher/problems")
public class ProblemGenerationController {

    private final ProblemGenerationService problemGenerationService;
    private final ProblemAsyncGenerationService asyncGenerationService;

    /**
     * 학습 문제 생성 조건에 맞는 STEP_FILL 문항을 조회한다.
     */
    @PostMapping("/generate")
    public ApiResponse<List<ProblemQuestionDetailResponse>> generate(
        @Valid
        @RequestBody
        ProblemGenerationRequest request,

        @AuthenticationPrincipal
        AuthenticatedUser user
    ) {
        List<ProblemQuestionDetailResponse> response =
            problemGenerationService.generate(request);

        return ApiResponse.success(response);
    }

    /** 기존 동기 API와 별도로 일반학습 문제 생성을 비동기 Job으로 접수한다. */
    @PostMapping("/generate/async")
    public ApiResponse<ProblemGenerationStartResponse> generateAsync(
            @Valid @RequestBody AsyncProblemGenerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(asyncGenerationService.startGeneral(user.memberId(), request));
    }

    /** 비동기 생성 Job과 문항별 현재 상태를 조회한다. */
    @GetMapping("/generation-jobs/{jobId}")
    public ApiResponse<ProblemGenerationJobStatusResponse> getJob(
            @PathVariable long jobId, @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(asyncGenerationService.getStatus(user.memberId(), jobId));
    }
}
