package com.cenedu.backend.domain.analysis.dto;

/**
 * 개인 보고서 생성 결과.
 *
 * <p>화면은 이 응답의 {@code pdfUrl} 로 곧바로 이동한다. 나머지 주소는 지금 열려 있지 않지만,
 * 프론트가 읽는 필드 이름이라 자리를 비워 두지 않고 같이 내려준다.
 */
public record StudentReportSummary(
        String reportId,
        String studentId,
        String assessmentId,
        String reportType,
        String statusName,
        String reportUrl,
        String htmlUrl,
        String pdfUrl
) {
    public static StudentReportSummary of(String reportId, String studentId, String assessmentId,
                                          String reportType, String statusName) {
        String base = "/api/reports/" + reportId;
        return new StudentReportSummary(reportId, studentId, assessmentId, reportType, statusName,
                base, base + "/html", base + "/pdf");
    }
}
