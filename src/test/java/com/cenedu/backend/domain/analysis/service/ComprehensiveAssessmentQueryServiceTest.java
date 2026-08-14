package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.dto.response.ComprehensiveAssessmentItemAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.ScoreTimeDistributionResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.entity.enums.AssessmentQuestionTypeGroup;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.ComprehensiveAssessmentQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentItemColumnRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.AssessmentStudentItemRow;
import com.cenedu.backend.domain.analysis.repository.row.ScoreTimeStudentRow;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComprehensiveAssessmentQueryServiceTest {

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final ComprehensiveAssessmentQueryRepository repository =
            mock(ComprehensiveAssessmentQueryRepository.class);
    private final ComprehensiveAssessmentQueryService service =
            new ComprehensiveAssessmentQueryService(
                    classQueryService,
                    repository,
                    new AnalysisStatusClassifier(),
                    new AnalysisMedianCalculator());

    @Test
    @DisplayName("학습평가 배정으로 종합평가 API를 호출하면 거부한다")
    void rejectsGeneralLearningAssignment() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(WorksheetType.GENERAL_LEARNING));

        assertThatThrownBy(() -> service.getInsights(7L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_ASSIGNMENT_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("유형과 난이도는 빈 구간까지 모두 반환하고 채점값이 없으면 참고값으로 표시한다")
    void fillsAllInsightGroups() {
        allowComprehensive();
        when(repository.findGroupAggregates(101L)).thenReturn(List.of(
                aggregate(AssessmentGroupAggregateRow.GroupDimension.QUESTION_TYPE,
                        "MULTIPLE_CHOICE", 2, 3, "66.7"),
                aggregate(AssessmentGroupAggregateRow.GroupDimension.DIFFICULTY,
                        "LOW", 1, 0, null)));
        when(repository.findPriorityItems(101L)).thenReturn(List.of(
                new AssessmentPriorityItemRow(501L, 2, "우선 문항", 3, 1, 4)));

        ComprehensiveAssessmentInsightsResponse response = service.getInsights(7L, 101L);

        assertThat(response.questionTypeGroups()).hasSize(3);
        assertThat(response.questionTypeGroups().getFirst().questionTypeGroup())
                .isEqualTo(AssessmentQuestionTypeGroup.MULTIPLE_CHOICE);
        assertThat(response.questionTypeGroups().getFirst().accuracyRate())
                .isEqualByComparingTo("66.7");
        assertThat(response.questionTypeGroups().get(1).referenceOnly()).isTrue();
        assertThat(response.difficultyBands()).hasSize(3);
        assertThat(response.difficultyBands()).extracting(
                        ComprehensiveAssessmentInsightsResponse.DifficultyBandResult
                                ::difficultyBand)
                .containsExactly(DifficultyBand.HIGH, DifficultyBand.MID, DifficultyBand.LOW);
        assertThat(response.difficultyBands().get(2).referenceOnly()).isTrue();
        assertThat(response.priorityItems().getFirst().difficultyBand())
                .isEqualTo(DifficultyBand.HIGH);
    }

    @Test
    @DisplayName("문항별 행을 학생 단위로 묶어 성취 행렬을 만든다")
    void groupsItemResultsByStudent() {
        allowComprehensive();
        when(repository.findItemColumns(101L)).thenReturn(List.of(
                new AssessmentItemColumnRow(501L, 1, new BigDecimal("20.00"))));
        when(repository.findStudentItemResults(101L)).thenReturn(List.of(
                new AssessmentStudentItemRow(
                        11L, "김민수", 501L, GradingStatus.GRADED,
                        new BigDecimal("15.00"), 30000L),
                new AssessmentStudentItemRow(
                        12L, "박지수", 501L, GradingStatus.NOT_GRADED,
                        null, null)));

        ComprehensiveAssessmentItemAchievementResponse response =
                service.getItemAchievement(7L, 101L);

        assertThat(response.items()).hasSize(1);
        assertThat(response.students()).hasSize(2);
        assertThat(response.students().getFirst().results().getFirst().score())
                .isEqualByComparingTo("15.00");
        assertThat(response.students().get(1).results().getFirst().gradingStatus())
                .isEqualTo(GradingStatus.NOT_GRADED);
    }

    @Test
    @DisplayName("학생 분포는 자료 부족 상태와 null 제외 중앙값을 함께 반환한다")
    void createsScoreTimeDistribution() {
        allowComprehensive();
        when(repository.findScoreTimeStudents(101L)).thenReturn(List.of(
                new ScoreTimeStudentRow(
                        11L, "김민수", 2, new BigDecimal("40.0"), 10000L),
                new ScoreTimeStudentRow(
                        12L, "박지수", 2, new BigDecimal("80.0"), 30000L),
                new ScoreTimeStudentRow(13L, "이서준", 0, null, null)));

        ScoreTimeDistributionResponse response = service.getScoreTimeDistribution(7L, 101L);

        assertThat(response.medianScoreRate()).isEqualByComparingTo("60.0");
        assertThat(response.medianSolvingDurationMs()).isEqualTo(20000L);
        assertThat(response.studentDistribution().get(2).analysisStatus())
                .isEqualTo(AnalysisStatus.INSUFFICIENT_DATA);
    }

    private void allowComprehensive() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(WorksheetType.COMPREHENSIVE_ASSESSMENT));
    }

    private AnalysisAssignmentAccessRow access(WorksheetType type) {
        return new AnalysisAssignmentAccessRow(
                101L, "종합평가", type, "1반", 7L, 7L);
    }

    private AssessmentGroupAggregateRow aggregate(
            AssessmentGroupAggregateRow.GroupDimension dimension,
            String code,
            int itemCount,
            int gradedCount,
            String rate
    ) {
        return new AssessmentGroupAggregateRow(
                dimension,
                code,
                itemCount,
                gradedCount,
                rate == null ? null : new BigDecimal(rate));
    }
}
