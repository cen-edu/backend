package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportGenerationResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisReportService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 학생 상세 화면의 AI 분석 문장 생성·조회 API. */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}/students/{studentId}")
@RequiredArgsConstructor
public class AnalysisReportController {

    private final AnalysisReportService reportService;

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<AnalysisReportGenerationResponse> generateReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(reportService.requestGeneration(
                user.memberId(), assignmentId, studentId));
    }

    @GetMapping("/report")
    public ApiResponse<AnalysisReportResponse> getReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(reportService.getReport(
                user.memberId(), assignmentId, studentId));
    }
}
