package com.cenedu.backend.domain.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.dashboard.dto.request.DashboardAssignmentListRequest;
import com.cenedu.backend.domain.dashboard.dto.request.DashboardClassRequest;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardAssignmentListResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardStudentProgressResponse;
import com.cenedu.backend.domain.dashboard.dto.response.DashboardSummaryResponse;
import com.cenedu.backend.domain.dashboard.entity.enums.AssignmentProgressStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardAssignmentStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardResultStatus;
import com.cenedu.backend.domain.dashboard.entity.enums.DashboardStudentStatus;
import com.cenedu.backend.domain.dashboard.repository.DashboardQueryRepository;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardAssignmentItemRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentProgressRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardStudentStatusRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardSummaryRow;
import com.cenedu.backend.domain.dashboard.repository.row.DashboardWorksheetColumnRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetOrigin;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DashboardQueryServiceTest {

    private final DashboardQueryRepository repository = mock(DashboardQueryRepository.class);
    private final DashboardQueryService service = new DashboardQueryService(
            repository, new DashboardStatusClassifier());

    @Test
    @DisplayName("담당하지 않는 반의 대시보드 조회를 거부한다")
    void rejectsOtherTeachersClass() {
        when(repository.findClassOwnerTeacherId(3L)).thenReturn(Optional.of(9L));

        assertThatThrownBy(() -> service.getSummary(7L, classRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.DASHBOARD_CLASS_ACCESS_DENIED);
    }

    @Test
    @DisplayName("요약 집계와 학생 상태별 인원수를 반환한다")
    void createsSummary() {
        allowClassAccess();
        when(repository.findSummary(3L, 2)).thenReturn(new DashboardSummaryRow(
                6, 4, new BigDecimal("74.0"), 8, 10, 1, 1));
        when(repository.findStudentStatuses(3L, 2)).thenReturn(List.of(
                new DashboardStudentStatusRow(11L, true, 2, new BigDecimal("90.0")),
                new DashboardStudentStatusRow(12L, false, 2, new BigDecimal("50.0")),
                new DashboardStudentStatusRow(13L, false, 2, new BigDecimal("80.0")),
                new DashboardStudentStatusRow(14L, false, 0, null)));

        DashboardSummaryResponse response = service.getSummary(7L, classRequest());

        assertThat(response.summary().assignmentCount()).isEqualTo(6);
        assertThat(response.summary().weaknessThresholdRate())
                .isEqualByComparingTo("60");
        assertThat(response.studentStatusCounts().delayed()).isEqualTo(1);
        assertThat(response.studentStatusCounts().needsSupport()).isEqualTo(1);
        assertThat(response.studentStatusCounts().good()).isEqualTo(1);
        assertThat(response.studentStatusCounts().insufficientData()).isEqualTo(1);
    }

    @Test
    @DisplayName("학생별 진행 상태와 학습지 유형별 결과값을 구성한다")
    void createsStudentProgress() {
        allowClassAccess();
        OffsetDateTime dueAt = OffsetDateTime.now().plusDays(3);
        when(repository.findWorksheetColumns(3L, 2)).thenReturn(List.of(
                new DashboardWorksheetColumnRow(
                        101L, "학습평가", WorksheetType.GENERAL_LEARNING,
                        WorksheetOrigin.STANDARD, null, dueAt.minusDays(1), dueAt),
                new DashboardWorksheetColumnRow(
                        102L, "종합평가", WorksheetType.COMPREHENSIVE_ASSESSMENT,
                        WorksheetOrigin.CUSTOM, 101L, dueAt, dueAt.plusDays(1))));
        when(repository.findStudentProgress(3L, 2)).thenReturn(List.of(
                progressRow(101L, AssignmentStatus.GRADED, 2, 1, null, dueAt),
                progressRow(102L, AssignmentStatus.GRADED, 1, 1,
                        new BigDecimal("85.0"), dueAt.plusDays(1))));

        DashboardStudentProgressResponse response =
                service.getStudentProgress(7L, classRequest());

        // 맞춤 열은 원본 배정을 달고 나가야 프론트가 원본 열과 묶을 수 있다.
        assertThat(response.worksheetColumns()).extracting(
                        DashboardStudentProgressResponse.WorksheetColumn::assignmentId,
                        DashboardStudentProgressResponse.WorksheetColumn::sourceAssignmentId)
                .containsExactly(tuple(101L, null), tuple(102L, 101L));

        DashboardStudentProgressResponse.StudentProgress student =
                response.students().getFirst();
        assertThat(student.status()).isEqualTo(DashboardStudentStatus.GOOD);
        assertThat(student.completedAssignmentCount()).isEqualTo(2);
        assertThat(student.totalAssignmentCount()).isEqualTo(2);
        assertThat(student.averageAccuracyRate()).isEqualByComparingTo("66.7");
        assertThat(student.worksheetResults()).extracting(
                        DashboardStudentProgressResponse.WorksheetResult::status)
                .containsExactly(
                        AssignmentProgressStatus.COMPLETED,
                        AssignmentProgressStatus.COMPLETED);
        assertThat(student.worksheetResults().getFirst().resultValue())
                .isEqualByComparingTo("50.0");
        assertThat(student.worksheetResults().get(1).resultValue())
                .isEqualByComparingTo("85.0");
    }

    @Test
    @DisplayName("학습지 목록의 페이지와 상태를 반환한다")
    void createsAssignmentPage() {
        allowClassAccess();
        OffsetDateTime now = OffsetDateTime.now();
        when(repository.countAssignments(3L, 2)).thenReturn(21L);
        when(repository.findAssignments(3L, 2, 0, 20)).thenReturn(List.of(
                new DashboardAssignmentItemRow(
                        101L, "학습평가", WorksheetType.GENERAL_LEARNING,
                        WorksheetOrigin.STANDARD, null, now.minusDays(2), now.plusDays(2),
                        3, 2, 1, 0),
                new DashboardAssignmentItemRow(
                        102L, "맞춤 학습", WorksheetType.COMPREHENSIVE_ASSESSMENT,
                        WorksheetOrigin.CUSTOM, 101L, now.minusDays(4), now.minusDays(1),
                        3, 3, 3, 3)));

        DashboardAssignmentListResponse response = service.getAssignments(
                7L, new DashboardAssignmentListRequest(3L, 2, null, null));

        assertThat(response.page().number()).isZero();
        assertThat(response.page().totalElements()).isEqualTo(21);
        assertThat(response.page().totalPages()).isEqualTo(2);
        assertThat(response.assignments()).extracting(
                        DashboardAssignmentListResponse.AssignmentItem::status)
                .containsExactly(
                        DashboardAssignmentStatus.IN_PROGRESS,
                        DashboardAssignmentStatus.COMPLETED);
        // 진행 축과 채점 축은 따로 간다 — 아래 행은 COMPLETED 이면서 확정까지 끝난 상태다.
        assertThat(response.assignments()).extracting(
                        DashboardAssignmentListResponse.AssignmentItem::sourceAssignmentId,
                        DashboardAssignmentListResponse.AssignmentItem::resultStatus)
                .containsExactly(
                        tuple(null, DashboardResultStatus.GRADING),
                        tuple(101L, DashboardResultStatus.RELEASED));
    }

    private DashboardStudentProgressRow progressRow(
            long assignmentId,
            AssignmentStatus status,
            int gradedCount,
            int correctCount,
            BigDecimal totalScore,
            OffsetDateTime dueAt
    ) {
        return new DashboardStudentProgressRow(
                11L, "김민수", assignmentId, status, 1, dueAt,
                gradedCount, correctCount, totalScore, dueAt.minusHours(1));
    }

    private void allowClassAccess() {
        when(repository.findClassOwnerTeacherId(3L)).thenReturn(Optional.of(7L));
    }

    private DashboardClassRequest classRequest() {
        return new DashboardClassRequest(3L, 2);
    }
}
