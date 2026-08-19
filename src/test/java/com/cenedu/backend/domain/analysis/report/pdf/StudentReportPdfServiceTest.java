package com.cenedu.backend.domain.analysis.report.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.dto.response.CustomLearningSessionListResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentComprehensiveAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentLearningAssessmentPerformanceResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;
import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.analysis.service.AnalysisReportService;
import com.cenedu.backend.domain.analysis.service.ComprehensiveAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.CustomLearningQueryService;
import com.cenedu.backend.domain.analysis.service.LearningAssessmentQueryService;
import com.cenedu.backend.domain.analysis.service.StudentDetailQueryService;
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

class StudentReportPdfServiceTest {

    private static final long TEACHER_ID = 7L;
    private static final long ASSIGNMENT_ID = 101L;
    private static final long STUDENT_ID = 11L;

    private final StudentDetailQueryService studentDetailQueryService =
            mock(StudentDetailQueryService.class);
    private final ComprehensiveAssessmentQueryService comprehensiveQueryService =
            mock(ComprehensiveAssessmentQueryService.class);
    private final LearningAssessmentQueryService learningQueryService =
            mock(LearningAssessmentQueryService.class);
    private final CustomLearningQueryService customLearningQueryService =
            mock(CustomLearningQueryService.class);
    private final AnalysisReportService reportService = mock(AnalysisReportService.class);

    private PdfRenderer renderer;
    private StudentReportPdfService service;

    @BeforeEach
    void setUp() {
        renderer = new PdfRenderer();
        service = new StudentReportPdfService(
                studentDetailQueryService,
                comprehensiveQueryService,
                learningQueryService,
                customLearningQueryService,
                reportService,
                new PdfTemplateConfig().pdfTemplateEngine(),
                renderer);

        when(studentDetailQueryService.getSummary(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(summary(WorksheetType.GENERAL_LEARNING));
        when(studentDetailQueryService.getItems(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(items(WorksheetType.GENERAL_LEARNING));
        when(learningQueryService.getStudentPerformance(
                TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID)).thenReturn(performance());
        when(customLearningQueryService.getSessions(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(new CustomLearningSessionListResponse(List.of()));
    }

    @AfterEach
    void tearDown() {
        renderer.close();
    }

    @Test
    @DisplayName("학생 분석 내용을 한국어 PDF 로 만든다")
    void rendersStudentReport() throws IOException {
        when(reportService.getReport(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(readyReport());

        String text = textOf(service.render(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID));

        assertThat(text).contains("학생 분석 보고서", "김민수", "1반", "소인수분해 학습평가");
        assertThat(text).contains("평가 영역별 성취", "이해", "계산");
        assertThat(text).contains("난이도별 성취", "중");
        assertThat(text).contains("부분정답");
        assertThat(text).contains("이항할 때 부호 바꾸기");
        assertThat(text).contains("영역");
    }

    @Test
    @DisplayName("AI 문장이 없으면 그 영역만 비우고 나머지를 출력한다")
    void rendersWithoutAiMessages() throws IOException {
        when(reportService.getReport(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(AnalysisReportResponse.notGenerated());

        String text = textOf(service.render(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID));

        assertThat(text).contains("AI 분석 문장이 아직 생성되지 않았습니다");
        assertThat(text).contains("수행 요약", "문항별 결과", "부분정답");
    }

    @Test
    @DisplayName("종합평가는 문항 유형별 비교를 담는다")
    void rendersComprehensiveAssessmentReport() throws IOException {
        when(studentDetailQueryService.getSummary(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(summary(WorksheetType.COMPREHENSIVE_ASSESSMENT));
        when(studentDetailQueryService.getItems(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(items(WorksheetType.COMPREHENSIVE_ASSESSMENT));
        when(comprehensiveQueryService.getStudentPerformance(
                TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID)).thenReturn(comprehensivePerformance());
        when(reportService.getReport(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(readyReport());

        String text = textOf(service.render(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID));

        assertThat(text).contains("문항 유형별 성취", "객관식", "서술형");
        assertThat(text).doesNotContain("평가 영역별 성취");
        // 문항 표의 분류 열도 평가 영역이 아니라 문항 유형이어야 한다
        assertThat(text).contains("문항 유형");
    }

    @Test
    @DisplayName("파일명에는 ID 만 쓴다")
    void buildsFileName() {
        assertThat(service.fileName(101L, 11L)).isEqualTo("analysis-report-101-11.pdf");
    }

    private String textOf(byte[] pdf) throws IOException {
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    private StudentAnalysisSummaryResponse summary(WorksheetType type) {
        return new StudentAnalysisSummaryResponse(
                STUDENT_ID, "김민수", "1반", "소인수분해 학습평가",
                type, AnalysisStatus.REVIEW,
                12, 10, 6,
                new BigDecimal("60.0"), new BigDecimal("52.4"),
                null, null,
                List.of(new StudentAnalysisSummaryResponse.WeakSubcategory(
                        31L, "일차방정식", 3, 5, new BigDecimal("40.0"))));
    }

    /**
     * 실제 조회 API 와 같은 방식으로 채운다. 종합평가는 평가 영역이 비고 문항 유형이 오며,
     * 학습평가는 그 반대다. 종합평가에 넣는 문항 유형에는 평가 영역이 없기 때문이다.
     */
    private StudentItemResultListResponse items(WorksheetType type) {
        boolean comprehensive = type == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        return new StudentItemResultListResponse(555L, List.of(
                new StudentItemResultListResponse.StudentItemResult(
                        501L, 9001L, 1, "소인수분해로 나타내기",
                        comprehensive ? null : EvaluationArea.UNDERSTANDING,
                        comprehensive
                                ? AssessmentQuestionTypeGroup.MULTIPLE_CHOICE : null,
                        DifficultyBand.MID,
                        GradingStatus.GRADED, StudentItemResultType.PARTIAL_CORRECT,
                        new BigDecimal("1"), new BigDecimal("2"),
                        null, null, 4, 8, new BigDecimal("50.0"), List.of())));
    }

    private StudentLearningAssessmentPerformanceResponse performance() {
        return new StudentLearningAssessmentPerformanceResponse(
                List.of(
                        new StudentLearningAssessmentPerformanceResponse
                                .EvaluationAreaComparison(
                                EvaluationArea.UNDERSTANDING, 3,
                                new BigDecimal("66.7"), new BigDecimal("55.0"), false),
                        new StudentLearningAssessmentPerformanceResponse
                                .EvaluationAreaComparison(
                                EvaluationArea.CALCULATION, 2,
                                new BigDecimal("50.0"), new BigDecimal("48.0"), false)),
                List.of(new StudentLearningAssessmentPerformanceResponse
                        .DifficultyBandComparison(
                        DifficultyBand.MID, 5,
                        new BigDecimal("60.0"), new BigDecimal("52.0"), false)),
                List.of());
    }

    private StudentComprehensiveAssessmentPerformanceResponse comprehensivePerformance() {
        return new StudentComprehensiveAssessmentPerformanceResponse(
                List.of(
                        new StudentComprehensiveAssessmentPerformanceResponse
                                .QuestionTypeGroupComparison(
                                AssessmentQuestionTypeGroup.MULTIPLE_CHOICE, 4,
                                new BigDecimal("75.0"), new BigDecimal("62.5"), false),
                        new StudentComprehensiveAssessmentPerformanceResponse
                                .QuestionTypeGroupComparison(
                                AssessmentQuestionTypeGroup.ESSAY, 2,
                                new BigDecimal("50.0"), new BigDecimal("35.0"), false)),
                List.of(new StudentComprehensiveAssessmentPerformanceResponse
                        .DifficultyBandComparison(
                        DifficultyBand.HIGH, 3,
                        new BigDecimal("33.3"), new BigDecimal("30.0"), false)));
    }

    private AnalysisReportResponse readyReport() {
        return new AnalysisReportResponse(
                GenerationStatus.READY,
                "정답률은 60.0%로 학급 평균 52.4%보다 높습니다.",
                null,
                List.of(new AnalysisReportResponse.ItemMessage(
                        501L,
                        "이항까지는 맞았으나 부호를 바꾸지 않고 옮겼습니다.",
                        "이항할 때 부호 바꾸기",
                        "각 줄에서 무엇을 옮겼는지 말하게 하며 다시 풀게 해 주세요.")),
                "부호 처리 실수가 반복됩니다.");
    }
}
