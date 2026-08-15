package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.request.AnalysisAssignmentListRequest;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisAssignmentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 취약점 분석 화면의 학습지·학급 요약·학생 목록 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments")
@RequiredArgsConstructor
public class AnalysisClassController {

    private final AnalysisClassQueryService queryService;

    @GetMapping
    public ApiResponse<AnalysisAssignmentListResponse> getAssignments(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @ParameterObject @ModelAttribute AnalysisAssignmentListRequest request
    ) {
        return ApiResponse.success(queryService.getAssignments(user.memberId(), request));
    }

    @GetMapping("/{assignmentId}/overview")
    public ApiResponse<ClassAnalysisOverviewResponse> getOverview(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getOverview(user.memberId(), assignmentId));
    }

    @GetMapping("/{assignmentId}/students")
    public ApiResponse<AnalysisStudentListResponse> getStudents(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        return ApiResponse.success(queryService.getStudents(user.memberId(), assignmentId));
    }
}
