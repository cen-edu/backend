package com.cenedu.backend.domain.analysis.controller;

import com.cenedu.backend.domain.analysis.report.pdf.ClassReportPdfService;
import com.cenedu.backend.domain.analysis.report.pdf.StudentReportPdfService;
import com.cenedu.backend.global.security.AuthenticatedUser;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 분석 결과 PDF 다운로드 API.
 *
 * <p><b>이 프로젝트에서 {@code ApiResponse} 로 감싸지 않는 유일한 API 다</b>(AGENTS.md 7절).
 * 본문이 바이너리라 감쌀 수 없다. 에러는 그대로 JSON 이므로 성공은 {@code application/pdf},
 * 실패는 {@code application/json} 으로 Content-Type 이 갈린다.
 *
 * <p>PDF 를 저장하지 않는다. 요청이 올 때 기존 분석 API 가 쓰는 것과 같은 데이터로 즉시 만든다.
 */
@RestController
@RequestMapping("/api/teacher/analysis/assignments/{assignmentId}")
@RequiredArgsConstructor
@Tag(name = "교사 - 분석 PDF",
        description = "취약점 분석 결과를 인쇄용 PDF 로 내려받는 API")
public class AnalysisReportPdfController {

    private final ClassReportPdfService classReportPdfService;
    private final StudentReportPdfService studentReportPdfService;

    @GetMapping(value = "/report.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "학급 분석 PDF", description = """
            학급 요약, 학생 목록, 유형·난이도별 성취, 우선 확인 문항, 학생 × 항목 행렬을 담는다.
            종합평가면 점수와 풀이시간 표가 더 붙는다.

            화면의 사본이 아니라 같은 데이터로 만든 인쇄용 문서다. 그래프는 서버가 CSS 로 다시
            그리므로 화면과 픽셀 단위로 같지는 않다.

            실패하면 본문이 PDF 가 아니라 JSON 이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "PDF 바이너리"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "내 학습지가 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "배정이 없음", content = @Content)
    })
    public ResponseEntity<Resource> downloadClassReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId
    ) {
        byte[] pdf = classReportPdfService.render(user.memberId(), assignmentId);
        return pdfResponse(pdf, classReportPdfService.fileName(assignmentId));
    }

    @GetMapping(value = "/students/{studentId}/report.pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "학생 분석 PDF", description = """
            수행 요약, 영역·난이도별 성취, 취약 소분류, 문항별 결과, 맞춤 학습 회차와
            AI 분석 문장을 담는다.

            AI 문장이 아직 생성되지 않았어도 실패하지 않는다. 그 영역만 비우고 점수와 채점
            결과는 그대로 출력한다.

            실패하면 본문이 PDF 가 아니라 JSON 이다.
            """)
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "PDF 바이너리"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403", description = "내 학습지가 아님", content = @Content),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "배정이 없거나 배정받지 않은 학생", content = @Content)
    })
    public ResponseEntity<Resource> downloadStudentReport(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable long assignmentId,
            @PathVariable long studentId
    ) {
        byte[] pdf = studentReportPdfService.render(user.memberId(), assignmentId, studentId);
        return pdfResponse(pdf, studentReportPdfService.fileName(assignmentId, studentId));
    }

    /**
     * 파일명은 ASCII 로만 만든다. 한글을 넣으면 RFC 5987 인코딩이 필요하고 브라우저마다
     * 다르게 처리해 파일명이 깨지거나 다운로드가 막힌다.
     */
    private ResponseEntity<Resource> pdfResponse(byte[] pdf, String fileName) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(fileName)
                        .build()
                        .toString())
                .body(new ByteArrayResource(pdf));
    }
}
