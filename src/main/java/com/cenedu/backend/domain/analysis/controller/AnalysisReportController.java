package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportGenerationResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisReportService;
import com.cenedu.backend.global.common.ApiResponse;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "교사 - 분석 보고서",
        description = "채점 결과를 근거로 AI가 만든 학습 분석 문장을 생성하고 조회하는 API")
public class AnalysisReportController {

    private final AnalysisReportService reportService;

    @PostMapping("/report")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "AI 분석 문장 생성", description = """
            생성을 시작만 시키고 즉시 응답한다. 문장 생성에 20~40초가 걸려 요청을 붙잡고 있을 수 없다.
            화면 진입 시마다 호출해도 된다 — 채점 결과가 그대로면 다시 만들지 않고 현재 상태만 돌려준다.

            응답의 retryAfterMs 간격으로 조회 API를 반복 호출하다가, generationStatus 가
            READY 또는 FAILED 가 되면 멈춘다. 이미 최신 문장이 있으면 READY 와 0 이 온다.

            채점이 끝나지 않은 학생은 생성할 수 없다. 근거가 될 결과가 없기 때문이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "202", description = "생성 요청 접수"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "채점이 완료되지 않음(ANALYSIS_REPORT_NOT_GRADED)",
                    content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "내 학습지가 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "배정이 없거나 배정받지 않은 학생", content = @Content)
    })
    public ApiResponse<AnalysisReportGenerationResponse> generateReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(reportService.requestGeneration(
                user.memberId(), assignmentId, studentId));
    }

    @GetMapping("/report")
    @Operation(summary = "AI 분석 문장 조회", description = """
            저장된 문장을 반환한다. 아직 만든 적이 없어도 404 가 아니라 generationStatus 가
            PENDING 인 200 이다. 화면이 "생성 전"과 "조회 실패"를 구분하지 않아도 되게 한 것이다.

            itemMessages 에는 채점이 끝난 문항만 담긴다. 학생이 손대지 않은 문항은 관찰할 풀이
            행동이 없어 문장을 만들지 않으므로, 배열 길이가 학습지 문항 수보다 적을 수 있다.
            worksheetItemId 로 문항 결과와 맞춘다. 미응답 문항은 overallObservation 이 함께 다룬다.

            customLearningMessage 는 AI 가 아니라 백엔드 규칙으로 만드는 문장이라 저장하지 않는다.
            규칙이 정해지기 전까지는 항상 null 이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "조회 성공. 생성 전이면 PENDING"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "내 학습지가 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "배정이 없거나 배정받지 않은 학생", content = @Content)
    })
    public ApiResponse<AnalysisReportResponse> getReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        return ApiResponse.success(reportService.getReport(
                user.memberId(), assignmentId, studentId));
    }
}
