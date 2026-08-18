package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.service.StudentDetailQueryService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 학습평가와 종합평가 학생 상세 화면의 공통 조회 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}/students/{studentId}")
@RequiredArgsConstructor
public class StudentDetailAnalysisController {

    private final StudentDetailQueryService queryService;

    @GetMapping("/summary")
    public ApiResponse<StudentAnalysisSummaryResponse> getSummary(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(queryService.getSummary(
                user.memberId(), assignmentId, studentId));
    }

    @GetMapping("/items")
    public ApiResponse<StudentItemResultListResponse> getItems(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(queryService.getItems(
                user.memberId(), assignmentId, studentId));
    }
}
