package com.cenedu.backend.domain.analysis.controller;

import java.io.IOException;

import com.cenedu.backend.domain.analysis.dto.StudentReportSummary;
import com.cenedu.backend.domain.analysis.service.StudentReportService;

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
    @PostMapping("/students/{studentId}")
    public ResponseEntity<StudentReportSummary> generate(
            @PathVariable String studentId,
            @RequestParam String assessmentId,
            @RequestParam(defaultValue = "STUDENT_DETAIL") String reportType) {
        return ResponseEntity.status(201).body(reports.generate(assessmentId, studentId));
    }

    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<FileSystemResource> pdf(@PathVariable String reportId)
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
