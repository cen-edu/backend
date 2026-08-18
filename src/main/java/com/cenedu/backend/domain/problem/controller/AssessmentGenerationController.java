package com.cenedu.backend.domain.problem.controller;

import java.util.List;

import com.cenedu.backend.domain.problem.dto.request.AsyncAssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.dto.request.AssessmentGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemGenerationStartResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.service.AssessmentGenerationService;
import com.cenedu.backend.domain.problem.service.ProblemAsyncGenerationService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/teacher/assessments")
public class AssessmentGenerationController {

    private final AssessmentGenerationService assessmentGenerationService;
    private final ProblemAsyncGenerationService asyncGenerationService;

    /**
     * 종합평가 생성 조건에 맞는 문항을 조회한다.
     */
    @PostMapping("/generate")
    public ApiResponse<List<ProblemQuestionDetailResponse>> generate(
        @Valid
        @RequestBody
        AssessmentGenerationRequest request,

        @AuthenticationPrincipal
        AuthenticatedUser user
    ) {
        List<ProblemQuestionDetailResponse> response =
            assessmentGenerationService.generate(request);

        return ApiResponse.success(response);
    }

    /** 기존 동기 API와 별도로 종합평가 생성을 비동기 Job으로 접수한다. */
    @PostMapping("/generate/async")
    public ApiResponse<ProblemGenerationStartResponse> generateAsync(
            @Valid @RequestBody AsyncAssessmentGenerationRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.success(asyncGenerationService.startAssessment(user.memberId(), request));
    }
}
