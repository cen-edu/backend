package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 종합평가 학급 분석 화면의 지표·문항 성취·점수 시간 분포 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}")
@RequiredArgsConstructor
public class ComprehensiveAssessmentAnalysisController {

    private final ComprehensiveAssessmentQueryService queryService;

    @GetMapping("/comprehensive-assessment-insights")
    public ApiResponse<ComprehensiveAssessmentInsightsResponse> getInsights(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getInsights(user.memberId(), assignmentId));
    }

    @GetMapping("/item-achievement")
    public ApiResponse<ComprehensiveAssessmentItemAchievementResponse> getItemAchievement(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getItemAchievement(
                user.memberId(), assignmentId));
    }

    @GetMapping("/score-time-distribution")
    public ApiResponse<ScoreTimeDistributionResponse> getScoreTimeDistribution(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getScoreTimeDistribution(
                user.memberId(), assignmentId));
    }
}
