package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.dto.request.AnalysisAssignmentListRequest;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisStudentListResponse;
import com.cenedu.backend.domain.analysis.dto.response.ClassAnalysisOverviewResponse;
import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.analysis.repository.AnalysisClassQueryRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisStudentRow;
import com.cenedu.backend.domain.analysis.repository.row.ClassAnalysisOverviewRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisClassQueryServiceTest {

    private final AnalysisClassQueryRepository repository =
            mock(AnalysisClassQueryRepository.class);
    private final AnalysisClassQueryService service = new AnalysisClassQueryService(
            repository, new AnalysisStatusClassifier());

    @Test
    @DisplayName("교사가 담당하지 않는 반의 학습지 목록은 조회할 수 없다")
    void rejectsClassOwnedByAnotherTeacher() {
        when(repository.findClassOwnerTeacherId(3L)).thenReturn(Optional.of(99L));

        assertThatThrownBy(() -> service.getAssignments(
                7L, new AnalysisAssignmentListRequest(3L, 2, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_CLASS_ACCESS_DENIED);
    }

    @Test
    @DisplayName("학습평가 요약은 풀이시간을 비우고 취약 개념 수를 반환한다")
    void returnsGeneralLearningOverviewFields() {
        when(repository.findAssignmentAccess(101L)).thenReturn(Optional.of(
                access(WorksheetType.GENERAL_LEARNING)));
        when(repository.findOverview(101L, WorksheetType.GENERAL_LEARNING))
                .thenReturn(new ClassAnalysisOverviewRow(
                8, 1, 2, new BigDecimal("64.3"), 120000L, 3, 2));

        ClassAnalysisOverviewResponse response = service.getOverview(7L, 101L);

        assertThat(response.summary().averageSolvingDurationMs()).isNull();
        assertThat(response.summary().weaknessSubcategoryCount()).isEqualTo(3);
        assertThat(response.summary().classPerformanceRate())
                .isEqualByComparingTo("64.3");
    }

    @Test
    @DisplayName("종합평가 요약은 풀이시간을 반환하고 취약 개념 수를 비운다")
    void returnsComprehensiveOverviewFields() {
        when(repository.findAssignmentAccess(101L)).thenReturn(Optional.of(
                access(WorksheetType.COMPREHENSIVE_ASSESSMENT)));
        when(repository.findOverview(101L, WorksheetType.COMPREHENSIVE_ASSESSMENT))
                .thenReturn(new ClassAnalysisOverviewRow(
                8, 1, 2, new BigDecimal("64.3"), 120000L, 3, 2));

        ClassAnalysisOverviewResponse response = service.getOverview(7L, 101L);

        assertThat(response.summary().averageSolvingDurationMs()).isEqualTo(120000L);
        assertThat(response.summary().weaknessSubcategoryCount()).isNull();
        assertThat(response.summary().classPerformanceRate())
                .isEqualByComparingTo("64.3");
    }

    @Test
    @DisplayName("학생 목록의 성취율과 채점 문항 수로 분석 상태를 만든다")
    void classifiesStudentRows() {
        when(repository.findAssignmentAccess(101L)).thenReturn(Optional.of(
                access(WorksheetType.GENERAL_LEARNING)));
        when(repository.findStudents(101L, WorksheetType.GENERAL_LEARNING))
                .thenReturn(List.of(
                new AnalysisStudentRow(11L, "김민수", 2, new BigDecimal("50.0")),
                new AnalysisStudentRow(12L, "박지수", 0, null)));

        AnalysisStudentListResponse response = service.getStudents(7L, 101L);

        assertThat(response.students())
                .extracting(AnalysisStudentListResponse.StudentItem::analysisStatus)
                .containsExactly(AnalysisStatus.INTENSIVE, AnalysisStatus.INSUFFICIENT_DATA);
        assertThat(response.students().getFirst().performanceRate())
                .isEqualByComparingTo("50.0");
        verify(repository).findStudents(101L, WorksheetType.GENERAL_LEARNING);
    }

    private AnalysisAssignmentAccessRow access(WorksheetType worksheetType) {
        return new AnalysisAssignmentAccessRow(
                101L, "1단원 평가", worksheetType, "1반", 7L, 7L);
    }
}
