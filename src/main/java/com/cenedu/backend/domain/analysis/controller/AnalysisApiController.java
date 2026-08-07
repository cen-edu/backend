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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "취약점 분석 (회차)",
        description = "회차·학급·학생 단위 조회와 학급 보고서. 화면 하나를 그리려면 학생 수만큼 "
                + "호출이 필요하다. 새 화면은 한 번에 받는 `/api/weakness-analysis` 쪽을 쓸 것.")
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

    @Operation(summary = "회차 목록",
            description = "교사가 지금까지 실시한 회차. 최근 회차가 먼저 온다. "
                    + "`problemCount` 는 그 회차에 실제로 응답이 기록된 문항 수다.")
    @GetMapping
    public List<AssessmentListItem> assessments() {
        return dashboard.assessments();
    }

    @Operation(summary = "학급 집계",
            description = """
                    영역별·난이도별 결과와 문항 표에 쓰는 집계.

                    `overall.correctRatePercent` 는 응답 수 기준이고, 백분율은 서버에서
                    반올림해 내려간다. 화면에서 다시 계산하면 반올림 차이로 숫자가 어긋난다.

                    `problems[].referenceSuccessRate` 는 **비율(0~1)이 아니라 백분율(0~100)**
                    이다. 원본 데이터가 주는 참고값이며 100 을 곱하지 않는다.

                    같은 평가 ID 로 저장된 제목·날짜가 서로 다르면 `409 ASSESSMENT_HEADER_CONFLICT`.
                    """)
    @GetMapping("/{assessmentId}/class-summary")
    public ClassDashboard classSummary(
            @Parameter(description = "학습지(평가) 식별자") @PathVariable String assessmentId) {
        return dashboard.summary(assessmentId);
    }

    @Operation(summary = "학생 회차 상세",
            description = "학생 한 명의 문항별 결과와 영역·난이도 집계. 학급 값과 나란히 비교할 수 "
                    + "있도록 `classCorrectRatePercent` 를 함께 담는다.")
    @GetMapping("/{assessmentId}/students/{studentId}/summary")
    public StudentDetail studentSummary(@PathVariable String assessmentId,
                                        @PathVariable String studentId) {
        return dashboard.studentDetail(assessmentId, studentId);
    }

    @Operation(summary = "학생 답과 정답",
            description = """
                    학생이 쓴 답과 맞는 답. 자기 답이므로 학생에게 돌려줘도 된다.

                    **회차를 마친 뒤에만** 볼 수 있다. 완료 전이면 `409 ASSESSMENT_NOT_COMPLETED`.

                    기록되지 않은 문항(`submissionFailed`)은 목록에서 빠지고 정답 수의 분모에도
                    들어가지 않는다. 그대로 두면 빈 답에 오답 표시가 붙어 학생이 자기가 틀린
                    것으로 읽는다.
                    """)
    @GetMapping("/{assessmentId}/students/{studentId}/review")
    public StudentReview studentReview(@PathVariable String assessmentId,
                                       @PathVariable String studentId) {
        return review.review(assessmentId, studentId);
    }

    @Operation(summary = "학급 보고서 (HTML)",
            description = "PDF 와 같은 내용을 HTML 로 돌려준다. 브라우저 없이 내용을 확인할 수 "
                    + "있어 PDF 변환 문제와 내용 문제를 갈라 보는 데 쓴다.")
    @GetMapping(value = "/{assessmentId}/class-report/html", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> classReportHtml(@PathVariable String assessmentId) {
        byte[] body = classReport.html(assessmentId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(body);
    }

    @Operation(summary = "학급 보고서 (PDF)",
            description = "화면의 \"학급 보고서 다운로드\" 버튼이 여는 주소. 서버에 브라우저가 "
                    + "없으면 `503 REPORT_RENDERER_UNAVAILABLE`.")
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
