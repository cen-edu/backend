package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentAchievementResponse;
import com.cenedu.backend.domain.analysis.dto.response.LearningAssessmentInsightsResponse;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.analysis.repository.LearningAssessmentQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentGroupAggregateRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningAssessmentPriorityItemRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningStudentSubcategoryRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryColumnRow;
import com.cenedu.backend.domain.analysis.repository.row.LearningSubcategoryWeaknessRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LearningAssessmentQueryServiceTest {

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final LearningAssessmentQueryRepository repository =
            mock(LearningAssessmentQueryRepository.class);
    private final LearningAssessmentQueryService service =
            new LearningAssessmentQueryService(classQueryService, repository);

    @Test
    @DisplayName("종합평가 배정으로 학습평가 API를 호출하면 거부한다")
    void rejectsComprehensiveAssessment() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(WorksheetType.COMPREHENSIVE_ASSESSMENT));

        assertThatThrownBy(() -> service.getInsights(7L, 101L))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_LEARNING_ASSESSMENT_TYPE_MISMATCH);
    }

    @Test
    @DisplayName("영역과 난이도는 빈 구간까지 화면 순서로 반환한다")
    void fillsAllInsightGroups() {
        allowLearningAssessment();
        when(repository.findGroupAggregates(101L)).thenReturn(List.of(
                aggregate(
                        LearningAssessmentGroupAggregateRow.GroupDimension
                                .EVALUATION_AREA,
                        "CALCULATION", 2, 4, "50.0"),
                aggregate(
                        LearningAssessmentGroupAggregateRow.GroupDimension.DIFFICULTY,
                        "LOW", 1, 2, "50.0")));
        when(repository.findPriorityItems(101L)).thenReturn(List.of(
                new LearningAssessmentPriorityItemRow(
                        501L, 2, "우선 문항", "CALCULATION", 1, 1, 2)));

        LearningAssessmentInsightsResponse response = service.getInsights(7L, 101L);

        assertThat(response.evaluationAreas()).hasSize(4);
        assertThat(response.evaluationAreas().get(1).evaluationArea())
                .isEqualTo(EvaluationArea.CALCULATION);
        assertThat(response.evaluationAreas().get(1).accuracyRate())
                .isEqualByComparingTo("50.0");
        assertThat(response.evaluationAreas().getFirst().referenceOnly()).isTrue();
        assertThat(response.difficultyBands())
                .extracting(LearningAssessmentInsightsResponse.DifficultyBandResult
                        ::difficultyBand)
                .containsExactly(
                        DifficultyBand.LOW,
                        DifficultyBand.MID,
                        DifficultyBand.HIGH);
        assertThat(response.difficultyBands().getFirst().referenceOnly()).isTrue();
        assertThat(response.priorityItems().getFirst().evaluationArea())
                .isEqualTo(EvaluationArea.CALCULATION);
    }

    @Test
    @DisplayName("소분류별 집계 행을 학생 단위 성취 행렬과 취약 순위로 만든다")
    void createsAchievementMatrix() {
        allowLearningAssessment();
        when(repository.findSubcategoryColumns(101L)).thenReturn(List.of(
                new LearningSubcategoryColumnRow(31L, "소인수분해"),
                new LearningSubcategoryColumnRow(32L, "최대공약수")));
        when(repository.findStudentSubcategoryResults(101L)).thenReturn(List.of(
                new LearningStudentSubcategoryRow(11L, "김민수", 31L, 1, 2),
                new LearningStudentSubcategoryRow(11L, "김민수", 32L, 0, 0),
                new LearningStudentSubcategoryRow(12L, "박지수", 31L, 2, 2),
                new LearningStudentSubcategoryRow(12L, "박지수", 32L, 1, 1)));
        when(repository.findSubcategoryWeaknesses(101L)).thenReturn(List.of(
                new LearningSubcategoryWeaknessRow(31L, "소인수분해", 1),
                new LearningSubcategoryWeaknessRow(32L, "최대공약수", 0)));

        LearningAssessmentAchievementResponse response =
                service.getAchievement(7L, 101L);

        assertThat(response.subcategories()).hasSize(2);
        assertThat(response.students()).hasSize(2);
        assertThat(response.students().getFirst().results()).hasSize(2);
        assertThat(response.students().getFirst().results().getFirst().correctCount())
                .isEqualTo(1);
        assertThat(response.subcategoryRanking().getFirst().weakStudentCount())
                .isEqualTo(1);
    }

    private void allowLearningAssessment() {
        when(classQueryService.getAuthorizedAssignment(7L, 101L))
                .thenReturn(access(WorksheetType.GENERAL_LEARNING));
    }

    private AnalysisAssignmentAccessRow access(WorksheetType type) {
        return new AnalysisAssignmentAccessRow(
                101L, "학습평가", type, "1반", 7L, 7L);
    }

    private LearningAssessmentGroupAggregateRow aggregate(
            LearningAssessmentGroupAggregateRow.GroupDimension dimension,
            String code,
            int itemCount,
            int gradedCount,
            String rate
    ) {
        return new LearningAssessmentGroupAggregateRow(
                dimension,
                code,
                itemCount,
                gradedCount,
                rate == null ? null : new BigDecimal(rate));
    }
}
