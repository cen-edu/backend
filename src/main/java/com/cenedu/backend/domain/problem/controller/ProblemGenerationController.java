package com.cenedu.backend.domain.problem.controller;

import java.util.List;

import com.cenedu.backend.domain.problem.dto.request.ProblemGenerationRequest;
import com.cenedu.backend.domain.problem.dto.response.ProblemQuestionDetailResponse;
import com.cenedu.backend.domain.problem.service.ProblemGenerationService;
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
@RequestMapping("/api/teacher/problems")
public class ProblemGenerationController {

    private final ProblemGenerationService problemGenerationService;

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
}
