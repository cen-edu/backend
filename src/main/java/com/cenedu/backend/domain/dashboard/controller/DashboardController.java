package com.cenedu.backend.domain.dashboard.controller;

import com.cenedu.backend.domain.dashboard.dto.request.DashboardAssignmentListRequest;
import com.cenedu.backend.domain.dashboard.dto.request.DashboardClassRequest;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardAssignmentListResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardStudentProgressResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.cenedu.backend.domain.dashboard.service.DashboardQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 교사 대시보드의 요약·학생 현황·학습지 목록 API. */
@RestController
@RequestMapping("/api/teacher/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardQueryService queryService;

    @GetMapping("/summary")
    public ApiResponse<DashboardSummaryResponse> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute DashboardClassRequest request
    ) {
        return ApiResponse.success(queryService.getSummary(user.memberId(), request));
    }

    @GetMapping("/student-progress")
    public ApiResponse<DashboardStudentProgressResponse> getStudentProgress(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute DashboardClassRequest request
    ) {
        return ApiResponse.success(queryService.getStudentProgress(user.memberId(), request));
    }

    @GetMapping("/assignments")
    public ApiResponse<DashboardAssignmentListResponse> getAssignments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute DashboardAssignmentListRequest request
    ) {
        return ApiResponse.success(queryService.getAssignments(user.memberId(), request));
    }
}
