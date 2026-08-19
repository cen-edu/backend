package com.cenedu.backend.domain.analysis.report.pdf;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.infra.pdf.PdfRenderer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * 학급 분석 PDF 를 만든다.
 *
 * <p>학생 보고서와 같은 원칙이다. 기존 조회 서비스를 그대로 부르고, 권한 검증도 그 서비스에
 * 맡긴다. 화면과 PDF 가 다른 계산을 하면 같은 학급의 수치가 두 곳에서 달라진다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClassReportPdfService {

    private static final String TEMPLATE = "class-report";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final AnalysisClassQueryService classQueryService;
    private final LearningAssessmentQueryService learningQueryService;
    private final ComprehensiveAssessmentQueryService comprehensiveQueryService;
    private final SpringTemplateEngine pdfTemplateEngine;
    private final PdfRenderer pdfRenderer;

    /** 학급 분석 결과를 PDF 바이트로 만든다. */
    public byte[] render(long teacherId, long assignmentId) {
        Context context = new Context();
        context.setVariable("view", buildView(teacherId, assignmentId));
        return pdfRenderer.render(pdfTemplateEngine.process(TEMPLATE, context), null);
    }

    public String fileName(long assignmentId) {
        return "class-analysis-report-%d.pdf".formatted(assignmentId);
    }

    private ClassReportView buildView(long teacherId, long assignmentId) {
        ClassAnalysisOverviewResponse overview = classQueryService
                .getOverview(teacherId, assignmentId);
        List<AnalysisStudentListResponse.StudentItem> students = classQueryService
                .getStudents(teacherId, assignmentId).students();

        return overview.context().worksheetType() == WorksheetType.COMPREHENSIVE_ASSESSMENT
                ? comprehensiveView(teacherId, assignmentId, overview, students)
                : learningView(teacherId, assignmentId, overview, students);
    }

    private ClassReportView learningView(
            long teacherId,
            long assignmentId,
            ClassAnalysisOverviewResponse overview,
            List<AnalysisStudentListResponse.StudentItem> students
    ) {
        LearningAssessmentInsightsResponse insights = learningQueryService
                .getInsights(teacherId, assignmentId);
        LearningAssessmentAchievementResponse achievement = learningQueryService
                .getAchievement(teacherId, assignmentId);

        List<StudentReportView.ComparisonBar> areas = insights.evaluationAreas().stream()
                .map(area -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(area.evaluationArea()),
                        area.itemCount(), area.accuracyRate(), null, area.referenceOnly()))
                .toList();
        List<StudentReportView.ComparisonBar> bands = insights.difficultyBands().stream()
                .map(band -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(band.difficultyBand()),
                        band.itemCount(), band.accuracyRate(), null, band.referenceOnly()))
                .toList();
        List<ClassReportView.PriorityItem> priority = insights.priorityItems().stream()
                .map(item -> new ClassReportView.PriorityItem(
                        item.itemNumber(),
                        item.questionTitle(),
                        ReportLabels.of(item.evaluationArea()),
                        ReportLabels.of(item.difficultyBand()),
                        item.correctStudentCount(),
                        item.gradedStudentCount()))
                .toList();

        return new ClassReportView(
                overview, students, "평가 영역별 성취", areas, bands, priority, "영역",
                subcategoryMatrix(achievement), List.of(), null, null,
                LocalDate.now().format(DATE));
    }

    private ClassReportView comprehensiveView(
            long teacherId,
            long assignmentId,
            ClassAnalysisOverviewResponse overview,
            List<AnalysisStudentListResponse.StudentItem> students
    ) {
        ComprehensiveAssessmentInsightsResponse insights = comprehensiveQueryService
                .getInsights(teacherId, assignmentId);
        ComprehensiveAssessmentItemAchievementResponse achievement = comprehensiveQueryService
                .getItemAchievement(teacherId, assignmentId);
        ScoreTimeDistributionResponse distribution = comprehensiveQueryService
                .getScoreTimeDistribution(teacherId, assignmentId);

        List<StudentReportView.ComparisonBar> groups = insights.questionTypeGroups().stream()
                .map(group -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(group.questionTypeGroup()),
                        group.itemCount(), group.accuracyRate(), null, group.referenceOnly()))
                .toList();
        List<StudentReportView.ComparisonBar> bands = insights.difficultyBands().stream()
                .map(band -> new StudentReportView.ComparisonBar(
                        ReportLabels.of(band.difficultyBand()),
                        band.itemCount(), band.accuracyRate(), null, band.referenceOnly()))
                .toList();
        List<ClassReportView.PriorityItem> priority = insights.priorityItems().stream()
                .map(item -> new ClassReportView.PriorityItem(
                        item.itemNumber(),
                        item.questionTitle(),
                        ReportLabels.of(item.questionTypeGroup()),
                        ReportLabels.of(item.difficultyBand()),
                        item.correctStudentCount(),
                        item.gradedStudentCount()))
                .toList();
        List<ClassReportView.ScoreTimeRow> scoreTimes = distribution.studentDistribution().stream()
                .map(student -> new ClassReportView.ScoreTimeRow(
                        student.studentName(),
                        String.valueOf(student.analysisStatus()),
                        student.scoreRate(),
                        student.totalSolvingDurationMs()))
                .toList();

        return new ClassReportView(
                overview, students, "문항 유형별 성취", groups, bands, priority, "문항 유형",
                itemMatrix(achievement), scoreTimes,
                distribution.medianScoreRate(), distribution.medianSolvingDurationMs(),
                LocalDate.now().format(DATE));
    }

    /** 소분류 × 학생 정답 수 행렬. 칸은 "정답/채점" 이고 정답률로 명도를 나눈다. */
    private ClassReportView.Matrix subcategoryMatrix(
            LearningAssessmentAchievementResponse achievement
    ) {
        List<String> columns = achievement.subcategories().stream()
                .map(LearningAssessmentAchievementResponse.SubcategoryColumn::subcategoryName)
                .toList();
        List<ClassReportView.Matrix.Row> rows = achievement.students().stream()
                .map(student -> new ClassReportView.Matrix.Row(
                        student.studentName(),
                        student.results().stream()
                                .map(result -> cell(
                                        result.correctCount(), result.gradedCount()))
                                .toList()))
                .toList();
        return new ClassReportView.Matrix("소분류별 성취", columns, rows);
    }

    /** 문항 × 학생 점수 행렬. 칸은 획득 점수이고 배점 대비 비율로 명도를 나눈다. */
    private ClassReportView.Matrix itemMatrix(
            ComprehensiveAssessmentItemAchievementResponse achievement
    ) {
        List<String> columns = achievement.items().stream()
                .map(item -> item.itemNumber() + "번")
                .toList();
        List<BigDecimal> maxScores = achievement.items().stream()
                .map(ComprehensiveAssessmentItemAchievementResponse
                        .AssessmentItemColumn::maxScore)
                .toList();
        List<ClassReportView.Matrix.Row> rows = achievement.students().stream()
                .map(student -> {
                    List<ClassReportView.Matrix.Cell> cells = new java.util.ArrayList<>();
                    for (int index = 0; index < student.results().size(); index++) {
                        var result = student.results().get(index);
                        BigDecimal max = index < maxScores.size() ? maxScores.get(index) : null;
                        cells.add(scoreCell(result.score(), max));
                    }
                    return new ClassReportView.Matrix.Row(student.studentName(), cells);
                })
                .toList();
        return new ClassReportView.Matrix("문항별 성취", columns, rows);
    }

    private ClassReportView.Matrix.Cell cell(int correctCount, int gradedCount) {
        if (gradedCount == 0) {
            return new ClassReportView.Matrix.Cell("-", "heat-none");
        }
        double rate = 100.0 * correctCount / gradedCount;
        return new ClassReportView.Matrix.Cell(
                correctCount + " / " + gradedCount, heatClass(rate));
    }

    private ClassReportView.Matrix.Cell scoreCell(BigDecimal score, BigDecimal maxScore) {
        if (score == null) {
            return new ClassReportView.Matrix.Cell("-", "heat-none");
        }
        if (maxScore == null || maxScore.signum() == 0) {
            return new ClassReportView.Matrix.Cell(score.toPlainString(), "heat-none");
        }
        double rate = score.doubleValue() / maxScore.doubleValue() * 100;
        return new ClassReportView.Matrix.Cell(score.toPlainString(), heatClass(rate));
    }

    /** 낮을수록 진하게. 눈에 먼저 들어와야 하는 쪽이 취약한 칸이다. */
    private String heatClass(double rate) {
        if (rate >= 80) {
            return "heat-none";
        }
        if (rate >= 60) {
            return "heat-low";
        }
        if (rate >= 40) {
            return "heat-mid";
        }
        return "heat-high";
    }
}
