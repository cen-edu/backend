package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.dto.response.StudentAnalysisSummaryResponse;
import com.cenedu.backend.domain.analysis.dto.response.StudentItemResultListResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.analysis.repository.StudentDetailQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnalysisSummaryRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentAnswerUnitRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentItemDetailRow;
import com.cenedu.backend.domain.analysis.repository.row.StudentWeakSubcategoryRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentDetailQueryServiceTest {

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final StudentDetailQueryRepository repository =
            mock(StudentDetailQueryRepository.class);
    private final StudentDetailQueryService service = new StudentDetailQueryService(
            classQueryService, repository, new AnalysisStatusClassifier());

    @Test
    @DisplayName("학습평가 요약은 취약 소분류를 반환하고 풀이시간은 노출하지 않는다")
    void returnsGeneralLearningSummaryWithoutDuration() {
        allowStudent(WorksheetType.GENERAL_LEARNING);
        when(repository.findSummary(101L, 11L)).thenReturn(new StudentAnalysisSummaryRow(
                "김민수", 4, 4, 1,
                new BigDecimal("25.0"), new BigDecimal("64.3"),
                new BigDecimal("45.0"), new BigDecimal("70.0"),
                30000L, 45000L));
        when(repository.findWeakSubcategories(101L, 11L)).thenReturn(List.of(
                new StudentWeakSubcategoryRow(
                        31L, "소인수분해", 3, 4, new BigDecimal("25.0"))));

        StudentAnalysisSummaryResponse response = service.getSummary(7L, 101L, 11L);

        assertThat(response.analysisStatus()).isEqualTo(AnalysisStatus.INTENSIVE);
        assertThat(response.performanceRate()).isEqualByComparingTo("25.0");
        assertThat(response.classPerformanceRate()).isEqualByComparingTo("64.3");
        assertThat(response.totalSolvingDurationMs()).isNull();
        assertThat(response.classAverageSolvingDurationMs()).isNull();
        assertThat(response.className()).isEqualTo("1반");
        assertThat(response.weaknessSubcategories()).hasSize(1);
        assertThat(response.weaknessSubcategories().getFirst().incorrectCount())
                .isEqualTo(3);
    }

    @Test
    @DisplayName("종합평가 요약은 완전정답률이 아니라 득점률을 반환한다")
    void returnsComprehensiveAssessmentScoreRate() {
        allowStudent(WorksheetType.COMPREHENSIVE_ASSESSMENT);
        when(repository.findSummary(101L, 11L)).thenReturn(new StudentAnalysisSummaryRow(
                "김민수", 2, 2, 1,
                new BigDecimal("50.0"), new BigDecimal("75.0"),
                new BigDecimal("70.0"), new BigDecimal("65.0"),
                30000L, 45000L));
        when(repository.findWeakSubcategories(101L, 11L)).thenReturn(List.of());

        StudentAnalysisSummaryResponse response = service.getSummary(7L, 101L, 11L);

        assertThat(response.performanceRate()).isEqualByComparingTo("70.0");
        assertThat(response.classPerformanceRate()).isEqualByComparingTo("65.0");
        assertThat(response.totalSolvingDurationMs()).isEqualTo(30000L);
        assertThat(response.classAverageSolvingDurationMs()).isEqualTo(45000L);
        assertThat(response.analysisStatus()).isEqualTo(AnalysisStatus.REVIEW);
    }

    @Test
    @DisplayName("종합평가 문항 결과는 답안 단위와 평가 영역을 함께 반환한다")
    void returnsAssessmentItemsWithAnswerUnits() {
        allowStudent(WorksheetType.COMPREHENSIVE_ASSESSMENT);
        when(repository.findAnswerUnits(101L, 11L)).thenReturn(List.of(
                new StudentAnswerUnitRow(
                        501L, 701L, 0, "계산", "EXECUTE",
                        GradingStatus.GRADED, "3", "4",
                        BigDecimal.ZERO, StudentItemResultType.INCORRECT)));
        when(repository.findItems(101L, 11L)).thenReturn(List.of(
                new StudentItemDetailRow(
                        501L, 601L, 1, "계산 문항", "SHORT_INPUT",
                        "CALCULATION", 2, GradingStatus.GRADED,
                        StudentItemResultType.INCORRECT,
                        BigDecimal.ZERO, BigDecimal.TEN,
                        20000L, 15000L, 1, 2, new BigDecimal("50.0"))));

        StudentItemResultListResponse response = service.getItems(7L, 101L, 11L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.assignmentStudentId()).isEqualTo(1001L);
        var item = response.items().getFirst();
        assertThat(item.questionTypeGroup())
                .isEqualTo(AssessmentQuestionTypeGroup.SHORT_ANSWER);
        assertThat(item.evaluationArea()).isEqualTo(EvaluationArea.CALCULATION);
        assertThat(item.answerUnits()).hasSize(1);
        assertThat(item.answerUnits().getFirst().diagnosticStage().name())
                .isEqualTo("EXECUTE");
    }

    @Test
    @DisplayName("학습지를 배정받지 않은 학생의 상세 조회는 거부한다")
    void rejectsStudentNotAssigned() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(WorksheetType.GENERAL_LEARNING));
        when(repository.findAssignmentStudentId(101L, 99L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSummary(7L, 101L, 99L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED);
    }

    private void allowStudent(WorksheetType type) {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(type));
        when(repository.findAssignmentStudentId(101L, 11L))
                .thenReturn(Optional.of(1001L));
    }

    private AnalysisAssignmentAccessRow access(WorksheetType type) {
        return new AnalysisAssignmentAccessRow(
                101L, "분석 학습지", type, "1반", 7L, 7L);
    }
}
