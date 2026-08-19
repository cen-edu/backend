package com.cenedu.backend.domain.analysis.report.pdf;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentComprehensiveAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentLearningAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisReportService;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.CustomLearningQueryService;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.StudentDetailQueryService;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.infra.pdf.PdfRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 학생 분석 PDF 를 만든다.
 *
 * <p>기존 조회 서비스를 그대로 부른다. 화면과 PDF 가 다른 계산을 하면 같은 학생의 수치가 두 곳에서
 * 달라지고, 어느 쪽이 맞는지 알 수 없게 된다.
 *
 * <p>권한 검증도 기존 서비스에 맡긴다. 첫 호출인 요약 조회가 배정 소유와 학생 배정 여부를 함께
 * 확인하므로 여기서 다시 검사하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StudentReportPdfService {

    private static final String TEMPLATE = "student-report";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final StudentDetailQueryService studentDetailQueryService;
    private final ComprehensiveAssessmentQueryService comprehensiveQueryService;
    private final LearningAssessmentQueryService learningQueryService;
    private final CustomLearningQueryService customLearningQueryService;
    private final AnalysisReportService reportService;
    private final SpringTemplateEngine pdfTemplateEngine;
    private final PdfRenderer pdfRenderer;

    /** 학생 한 명의 분석 결과를 PDF 바이트로 만든다. */
    public byte[] render(long teacherId, long assignmentId, long studentId) {
        StudentReportView view = buildView(teacherId, assignmentId, studentId);
        Context context = new Context();
        context.setVariable("view", view);
        return pdfRenderer.render(pdfTemplateEngine.process(TEMPLATE, context), null);
    }

    /** 파일명. 한글을 넣으면 Content-Disposition 인코딩이 브라우저마다 달라져 ID 만 쓴다. */
    public String fileName(long assignmentId, long studentId) {
        return "analysis-report-%d-%d.pdf".formatted(assignmentId, studentId);
    }

    private StudentReportView buildView(long teacherId, long assignmentId, long studentId) {
        StudentAnalysisSummaryResponse summary = studentDetailQueryService
                .getSummary(teacherId, assignmentId, studentId);
        StudentItemResultListResponse items = studentDetailQueryService
                .getItems(teacherId, assignmentId, studentId);
        AnalysisReportResponse report = reportService
                .getReport(teacherId, assignmentId, studentId);
        List<StudentReportView.CustomSession> sessions = customLearningQueryService
                .getSessions(teacherId, assignmentId, studentId).sessions().stream()
                .map(this::toCustomSession)
                .toList();

        boolean comprehensive = summary.worksheetType()
                == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        Comparison comparison = comprehensive
                ? comprehensiveComparison(teacherId, assignmentId, studentId)
                : learningComparison(teacherId, assignmentId, studentId);

        return new StudentReportView(
                summary,
                comparison.title(),
                comprehensive ? "문항 유형" : "영역",
                comparison.bars(),
                comparison.difficultyBars(),
                toItemRows(items, report, comprehensive),
                report,
                sessions,
                LocalDate.now().format(DATE));
    }

    private Comparison comprehensiveComparison(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        StudentComprehensiveAssessmentPerformanceResponse performance =
                comprehensiveQueryService.getStudentPerformance(
                        teacherId, assignmentId, studentId);
        List<StudentReportView.ComparisonBar> groups = performance.questionTypeGroups().stream()
                .map(group -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(group.questionTypeGroup()),
                        group.itemCount(),
                        group.studentAccuracyRate(),
                        group.classAccuracyRate(),
                        group.referenceOnly()))
                .toList();
        List<StudentReportView.ComparisonBar> bands = performance.difficultyBands().stream()
                .map(band -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(band.difficultyBand()),
                        band.itemCount(),
                        band.studentAccuracyRate(),
                        band.classAccuracyRate(),
                        band.referenceOnly()))
                .toList();
        return new Comparison("문항 유형별 성취", groups, bands);
    }

    private Comparison learningComparison(long teacherId, long assignmentId, long studentId) {
        StudentLearningAssessmentPerformanceResponse performance =
                learningQueryService.getStudentPerformance(teacherId, assignmentId, studentId);
        List<StudentReportView.ComparisonBar> areas = performance.evaluationAreas().stream()
                .map(area -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(area.evaluationArea()),
                        area.itemCount(),
                        area.studentAccuracyRate(),
                        area.classAccuracyRate(),
                        area.referenceOnly()))
                .toList();
        List<StudentReportView.ComparisonBar> bands = performance.difficultyBands().stream()
                .map(band -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(band.difficultyBand()),
                        band.itemCount(),
                        band.studentAccuracyRate(),
                        band.classAccuracyRate(),
                        band.referenceOnly()))
                .toList();
        return new Comparison("평가 영역별 성취", areas, bands);
    }

    /**
     * 문항 결과와 AI 문장을 {@code worksheetItemId} 로 맞춰 한 줄로 합친다.
     *
     * <p>AI 문장은 채점 완료 문항에만 있어 개수가 문항 수보다 적다. 없는 문항은 세 값이 모두
     * {@code null} 이 되고 템플릿이 그 문항의 지도 참고를 건너뛴다.
     */
    private List<StudentReportView.ItemRow> toItemRows(
            StudentItemResultListResponse items,
            AnalysisReportResponse report,
            boolean comprehensive
    ) {
        Map<Long, AnalysisReportResponse.ItemMessage> messages = report.itemMessages().stream()
                .collect(Collectors.toMap(
                        AnalysisReportResponse.ItemMessage::worksheetItemId,
                        Function.identity()));
        return items.items().stream()
                .map(item -> {
                    AnalysisReportResponse.ItemMessage message =
                            messages.get(item.worksheetItemId());
                    return new StudentReportView.ItemRow(
                            item.itemNumber(),
                            item.questionTitle(),
                            comprehensive
                                    ? ReportLabels.of(item.questionTypeGroup())
                                    : ReportLabels.of(item.evaluationArea()),
                            ReportLabels.of(item.difficultyBand()),
                            ReportLabels.of(item.resultType()),
                            item.score(),
                            item.maxScore(),
                            item.classAccuracyRate(),
                            message == null ? null : message.observation(),
                            message == null ? null : message.learningPoint(),
                            message == null ? null : message.retryGuide());
                })
                .toList();
    }

    /** 맞춤 학습 회차를 인쇄용으로 옮긴다. 상태와 난이도를 한국어로 바꾸는 자리다. */
    private StudentReportView.CustomSession toCustomSession(
            CustomLearningSessionListResponse.CustomLearningSession session
    ) {
        return new StudentReportView.CustomSession(
                session.assignedAt() == null ? "-" : session.assignedAt().toLocalDate()
                        .format(DATE),
                ReportLabels.of(session.overallResolutionStatus()),
                session.completedItemCount(),
                session.totalItemCount(),
                session.subcategories().stream()
                        .map(sub -> new StudentReportView.CustomSubcategory(
                                sub.subcategoryName(),
                                ReportLabels.of(sub.resolutionStatus()),
                                ReportLabels.of(sub.currentDifficultyBand()),
                                sub.accuracyRate()))
                        .toList());
    }

    private record Comparison(
            String title,
            List<StudentReportView.ComparisonBar> bars,
            List<StudentReportView.ComparisonBar> difficultyBars
    ) {
    }
}
