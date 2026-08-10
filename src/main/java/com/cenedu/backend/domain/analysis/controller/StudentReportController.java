package com.cenedu.backend.domain.analysis.controller;

import java.io.IOException;

import com.cenedu.backend.domain.analysis.dto.StudentReportSummary;
import com.cenedu.backend.domain.analysis.service.StudentReportService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 학생 개인 보고서.
 *
 * <p>화면은 생성 요청을 보낸 뒤 응답의 {@code pdfUrl} 로 곧바로 이동한다. 그래서 생성 시점에
 * PDF 를 다 만들어 두고 주소만 넘긴다.
 *
 * <p>경로와 응답 모양이 AGENTS.md 2절·7절과 다른 이유는 {@link AnalysisApiController} 의 설명과
 * 같다. 프론트 계약에 맞춘 것이다.
 */
@Tag(name = "개인 보고서",
        description = "학생 한 명의 개인 분석 PDF. 생성과 내려받기가 두 단계로 나뉜다.")
@RestController
@RequestMapping("/api/reports")
public class StudentReportController {

    private final StudentReportService reports;

    public StudentReportController(StudentReportService reports) {
        this.reports = reports;
    }

    /**
     * 개인 보고서를 만든다.
     *
     * <p>{@code reportType} 은 프론트가 보내지만 지금은 개인 상세 한 종류뿐이라 쓰지 않는다.
     * 받아만 두는 이유는 빼면 프론트 요청이 400 이 되기 때문이다.
     */
    @Operation(summary = "개인 보고서 생성",
            description = """
                    개인 분석 PDF 를 만들고 내려받을 주소를 돌려준다. 응답의 `pdfUrl` 로 바로
                    이동하면 된다.

                    **PDF 를 여기서 다 만들고 주소를 넘긴다.** 주소를 먼저 주고 뒤에서 만들면
                    화면이 빈 파일을 받는다. 그래서 이 호출은 문항 수에 따라 몇 초가 걸린다.

                    부를 때마다 새 보고서가 만들어진다. 같은 학생·회차로 여러 번 부르면
                    `reportId` 가 매번 다르고, 이전 것도 그대로 남는다.

                    `reportType` 은 프론트가 보내지만 지금은 개인 상세 한 종류뿐이라 쓰이지 않는다.

                    LLM 서술은 들어가지 않는다. 화면에 이미 있는 값만 인쇄물로 옮긴 것이다.
                    """)
    @PostMapping("/students/{studentId}")
    public ResponseEntity<StudentReportSummary> generate(
            @Parameter(description = "학생 식별자", example = "SIM-S10")
            @PathVariable String studentId,
            @Parameter(description = "학습지(평가) 식별자") @RequestParam String assessmentId,
            @Parameter(description = "지금은 STUDENT_DETAIL 하나뿐")
            @RequestParam(defaultValue = "STUDENT_DETAIL") String reportType) {
        return ResponseEntity.status(201).body(reports.generate(assessmentId, studentId));
    }

    @Operation(summary = "개인 보고서 내려받기 (PDF)",
            description = "생성 응답의 `pdfUrl` 이 가리키는 주소. 없는 번호이거나 PDF 가 없으면 "
                    + "`404 REPORT_NOT_FOUND`.")
    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> pdf(
            @Parameter(description = "생성 응답의 reportId (UUID)") @PathVariable String reportId)
            throws IOException {
        FileSystemResource resource = new FileSystemResource(reports.pdfPath(reportId));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=student-report.pdf")
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
