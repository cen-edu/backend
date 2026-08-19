package com.cenedu.backend.domain.analysis.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.service.AnalysisClassQueryService;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.infra.pdf.PdfRenderer;
import com.cenedu.backend.infra.pdf.PdfTemplateConfig;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ClassReportPdfServiceTest {

    private static final long TEACHER_ID = 7L;
    private static final long ASSIGNMENT_ID = 101L;

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final LearningAssessmentQueryService learningQueryService =
            mock(LearningAssessmentQueryService.class);
    private final ComprehensiveAssessmentQueryService comprehensiveQueryService =
            mock(ComprehensiveAssessmentQueryService.class);

    private PdfRenderer renderer;
    private ClassReportPdfService service;

    @BeforeEach
    void setUp() {
        renderer = new PdfRenderer();
        service = new ClassReportPdfService(
                classQueryService, learningQueryService, comprehensiveQueryService,
                new PdfTemplateConfig().pdfTemplateEngine(), renderer);
    }

    @AfterEach
    void tearDown() {
        renderer.close();
    }

    @Test
    @DisplayName("학습평가는 평가 영역과 소분류 행렬을 담는다")
    void rendersLearningAssessmentReport() throws IOException {
        givenOverview(WorksheetType.GENERAL_LEARNING);
        when(learningQueryService.getInsights(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(insights());
        when(learningQueryService.getAchievement(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(achievement());

        String text = textOf(service.render(TEACHER_ID, ASSIGNMENT_ID));

        assertThat(text).contains("학급 분석 보고서", "1반", "소인수분해 학습평가");
        assertThat(text).contains("평가 영역별 성취", "이해");
        assertThat(text).contains("우선 확인 문항", "소인수분해로 나타내기");
        assertThat(text).contains("소분류별 성취", "일차방정식");
        assertThat(text).contains("김민수");
        assertThat(text).doesNotContain("점수와 풀이시간");
    }

    @Test
    @DisplayName("종합평가는 문항 유형과 문항 행렬, 점수·시간 표를 담는다")
    void rendersComprehensiveAssessmentReport() throws IOException {
        givenOverview(WorksheetType.COMPREHENSIVE_ASSESSMENT);
        when(comprehensiveQueryService.getInsights(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(comprehensiveInsights());
        when(comprehensiveQueryService.getItemAchievement(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(itemAchievement());
        when(comprehensiveQueryService.getScoreTimeDistribution(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(distribution());

        String text = textOf(service.render(TEACHER_ID, ASSIGNMENT_ID));

        assertThat(text).contains("문항 유형별 성취", "객관식", "서술형");
        assertThat(text).contains("문항별 성취", "1번");
        assertThat(text).contains("점수와 풀이시간", "30분 30초");
        assertThat(text).doesNotContain("평가 영역별 성취");
    }

    @Test
    @DisplayName("파일명에는 배정 ID 만 쓴다")
    void buildsFileName() {
        assertThat(service.fileName(101L)).isEqualTo("class-analysis-report-101.pdf");
    }

    private void givenOverview(WorksheetType type) {
        when(classQueryService.getOverview(TEACHER_ID, ASSIGNMENT_ID)).thenReturn(
                new ClassAnalysisOverviewResponse(
                        new ClassAnalysisOverviewResponse.AnalysisContext(
                                "소인수분해 학습평가", type, "1반", OffsetDateTime.now()),
                        new ClassAnalysisOverviewResponse.ClassSummary(
                                8, 1, 2, new BigDecimal("52.4"), null, 3, 2)));
        when(classQueryService.getStudents(TEACHER_ID, ASSIGNMENT_ID)).thenReturn(
                new AnalysisStudentListResponse(List.of(
                        new AnalysisStudentListResponse.StudentItem(
                                11L, "김민수", AnalysisStatus.REVIEW, new BigDecimal("60.0")))));
    }

    private LearningAssessmentInsightsResponse insights() {
        return new LearningAssessmentInsightsResponse(
                List.of(new LearningAssessmentInsightsResponse.EvaluationAreaResult(
                        EvaluationArea.UNDERSTANDING, 3, new BigDecimal("55.0"), false)),
                List.of(new LearningAssessmentInsightsResponse.DifficultyBandResult(
                        DifficultyBand.MID, 5, new BigDecimal("48.0"), false)),
                List.of(new LearningAssessmentInsightsResponse.LearningAssessmentPriorityItem(
                        501L, 1, "소인수분해로 나타내기",
                        EvaluationArea.UNDERSTANDING, DifficultyBand.MID, 2, 8)));
    }

    private LearningAssessmentAchievementResponse achievement() {
        return new LearningAssessmentAchievementResponse(
                List.of(new LearningAssessmentAchievementResponse.SubcategoryColumn(
                        31L, "일차방정식")),
                List.of(new LearningAssessmentAchievementResponse
                        .LearningAssessmentStudentAchievement(
                        11L, "김민수",
                        List.of(new LearningAssessmentAchievementResponse.SubcategoryResult(
                                31L, 1, 3)))),
                List.of(new LearningAssessmentAchievementResponse.SubcategoryWeakness(
                        31L, "일차방정식", 4)));
    }

    private ComprehensiveAssessmentInsightsResponse comprehensiveInsights() {
        return new ComprehensiveAssessmentInsightsResponse(
                List.of(
                        new ComprehensiveAssessmentInsightsResponse.QuestionTypeGroupResult(
                                AssessmentQuestionTypeGroup.MULTIPLE_CHOICE, 4,
                                new BigDecimal("62.5"), false),
                        new ComprehensiveAssessmentInsightsResponse.QuestionTypeGroupResult(
                                AssessmentQuestionTypeGroup.ESSAY, 2,
                                new BigDecimal("35.0"), false)),
                List.of(new ComprehensiveAssessmentInsightsResponse.DifficultyBandResult(
                        DifficultyBand.HIGH, 3, new BigDecimal("30.0"), false)),
                List.of(new ComprehensiveAssessmentInsightsResponse
                        .ComprehensiveAssessmentPriorityItem(
                        501L, 1, "이차방정식의 근", DifficultyBand.HIGH, 2, 8)));
    }

    private ComprehensiveAssessmentItemAchievementResponse itemAchievement() {
        return new ComprehensiveAssessmentItemAchievementResponse(
                List.of(new ComprehensiveAssessmentItemAchievementResponse
                        .AssessmentItemColumn(501L, 1, new BigDecimal("10"))),
                List.of(new ComprehensiveAssessmentItemAchievementResponse
                        .AssessmentStudentAchievement(
                        11L, "김민수",
                        List.of(new ComprehensiveAssessmentItemAchievementResponse
                                .AssessmentItemResult(
                                501L, GradingStatus.GRADED, new BigDecimal("4"), 90000L)))));
    }

    private ScoreTimeDistributionResponse distribution() {
        return new ScoreTimeDistributionResponse(
                List.of(new ScoreTimeDistributionResponse.StudentDistribution(
                        11L, "김민수", AnalysisStatus.REVIEW,
                        new BigDecimal("62.0"), 1830000L)),
                new BigDecimal("58.0"), 1800000L);
    }

    private String textOf(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
