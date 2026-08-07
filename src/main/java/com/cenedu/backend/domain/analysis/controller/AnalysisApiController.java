package com.cenedu.backend.domain.analysis.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.AssessmentListItem;
import com.cenedu.backend.domain.analysis.dto.ClassDashboard;
import com.cenedu.backend.domain.analysis.dto.StudentDetail;
import com.cenedu.backend.domain.analysis.dto.StudentReview;
import com.cenedu.backend.domain.analysis.service.ClassDashboardService;
import com.cenedu.backend.domain.analysis.service.ClassOverviewReportService;
import com.cenedu.backend.domain.analysis.service.StudentReviewService;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 취약점 분석 화면이 부르는 조회 API.
 *
 * <p><b>경로와 응답 모양이 AGENTS.md 와 다르다.</b> 2절은 {@code /api/teacher/analysis} 접두어를,
 * 7절은 {@code ApiResponse<T>} 래핑을 요구하는데 여기서는 둘 다 따르지 않는다. 프론트 연동
 * 계층이 이미 이 계약에 맞춰 작성돼 있고, 프론트를 고치지 않기로 정했기 때문이다.
 *
 * <p>대신 두 가지를 알고 있어야 한다.
 * <ul>
 *   <li>권한이 URL 접두어로 갈리므로, 인증이 들어올 때 이 경로들은 {@code /api/teacher/**}
 *       규칙에 걸리지 않는다. SecurityConfig 에 명시적 규칙을 따로 넣어야 한다.</li>
 *   <li>오류 응답만은 {@code ApiResponse} 로 나간다. GlobalExceptionHandler 가 단일 지점이라
 *       여기서 벗기지 않는다. 프론트는 오류 본문에서 message 를 찾지 못하면 statusText 로
 *       넘어가므로 화면은 깨지지 않는다.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/assessments")
public class AnalysisApiController {

    private final ClassDashboardService dashboard;
    private final StudentReviewService review;
    private final ClassOverviewReportService classReport;

    public AnalysisApiController(ClassDashboardService dashboard, StudentReviewService review,
                                 ClassOverviewReportService classReport) {
        this.dashboard = dashboard;
        this.review = review;
        this.classReport = classReport;
    }

    /** 학습지 선택 목록. */
    @GetMapping
    public List<AssessmentListItem> assessments() {
        return dashboard.assessments();
    }

    /** 학급 집계. 화면의 영역별·난이도별 결과와 문항 표가 여기서 나온다. */
    @GetMapping("/{assessmentId}/class-summary")
    public ClassDashboard classSummary(@PathVariable String assessmentId) {
        return dashboard.summary(assessmentId);
    }

    /** 학생 한 명의 회차 상세. */
    @GetMapping("/{assessmentId}/students/{studentId}/summary")
    public StudentDetail studentSummary(@PathVariable String assessmentId,
                                        @PathVariable String studentId) {
        return dashboard.studentDetail(assessmentId, studentId);
    }

    /** 학생이 쓴 답과 정답. 화면의 문항 매트릭스가 학생 답을 여기서 읽는다. */
    @GetMapping("/{assessmentId}/students/{studentId}/review")
    public StudentReview studentReview(@PathVariable String assessmentId,
                                       @PathVariable String studentId) {
        return review.review(assessmentId, studentId);
    }

    /**
     * 학급 보고서 HTML. 브라우저 없이 내용을 확인할 수 있어 PDF 변환 문제와 내용 문제를
     * 갈라 보는 데 쓴다.
     */
    @GetMapping(value = "/{assessmentId}/class-report/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> classReportHtml(@PathVariable String assessmentId) {
        byte[] body = classReport.html(assessmentId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(body);
    }

    /** 학급 보고서 PDF. 화면의 "학급 보고서 다운로드" 버튼이 이 주소를 연다. */
    @GetMapping(value = "/{assessmentId}/class-report/pdf",
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> classReportPdf(@PathVariable String assessmentId)
            throws IOException {
        FileSystemResource resource = new FileSystemResource(classReport.pdf(assessmentId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=class-overview-report.pdf")
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
