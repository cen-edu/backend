package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습평가 학급 분석 화면의 지표와 소분류 성취 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}")
@RequiredArgsConstructor
public class LearningAssessmentAnalysisController {

    private final LearningAssessmentQueryService queryService;

    @GetMapping("/learning-assessment-insights")
    public ApiResponse<LearningAssessmentInsightsResponse> getInsights(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getInsights(user.memberId(), assignmentId));
    }

    @GetMapping("/learning-assessment-achievement")
    public ApiResponse<LearningAssessmentAchievementResponse> getAchievement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getAchievement(
                user.memberId(), assignmentId));
    }
}
