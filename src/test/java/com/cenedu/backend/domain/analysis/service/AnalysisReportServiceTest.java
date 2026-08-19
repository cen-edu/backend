package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportGenerationResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportItemMessageRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportQueryRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisAssignmentAccessRow;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisReportSourceRow;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisReportServiceTest {

    private static final long TEACHER_ID = 7L;
    private static final long ASSIGNMENT_ID = 101L;
    private static final long STUDENT_ID = 11L;
    private static final long ASSIGNMENT_STUDENT_ID = 555L;

    private final AnalysisClassQueryService classQueryService =
            mock(AnalysisClassQueryService.class);
    private final AnalysisReportRepository reportRepository =
            mock(AnalysisReportRepository.class);
    private final AnalysisReportItemMessageRepository itemMessageRepository =
            mock(AnalysisReportItemMessageRepository.class);
    private final AnalysisReportQueryRepository queryRepository =
            mock(AnalysisReportQueryRepository.class);
    private final ApplicationEventPublisher eventPublisher =
            mock(ApplicationEventPublisher.class);

    private final AnalysisReportService service = new AnalysisReportService(
            classQueryService,
            reportRepository,
            itemMessageRepository,
            queryRepository,
            eventPublisher);

    @Test
    @DisplayName("채점이 끝나지 않은 학생은 생성 요청을 거부한다")
    void rejectsGenerationBeforeGrading() {
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("SUBMITTED", OffsetDateTime.now(), null)));

        assertThatThrownBy(() ->
                service.requestGeneration(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_REPORT_NOT_GRADED);
        verify(reportRepository, never()).startGeneration(anyLong(), any(), any());
    }

    @Test
    @DisplayName("배정되지 않은 학생은 생성 요청을 거부한다")
    void rejectsUnassignedStudent() {
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.requestGeneration(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED);
    }

    @Test
    @DisplayName("채점 이후 변경이 없는 READY 보고서는 다시 만들지 않는다")
    void skipsGenerationWhenReportIsUpToDate() {
        OffsetDateTime gradedAt = OffsetDateTime.now().minusHours(1);
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("GRADED", gradedAt, null)));
        when(reportRepository.findByAssignmentStudentId(ASSIGNMENT_STUDENT_ID))
                .thenReturn(Optional.of(readyReport(gradedAt.plusMinutes(1))));

        AnalysisReportGenerationResponse response =
                service.requestGeneration(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID);

        assertThat(response.generationStatus()).isEqualTo(GenerationStatus.READY);
        assertThat(response.retryAfterMs()).isZero();
        verify(reportRepository, never()).startGeneration(anyLong(), any(), any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("교사가 점수를 고쳤으면 READY 보고서도 다시 만든다")
    void regeneratesWhenScoreOverriddenAfterGeneration() {
        OffsetDateTime gradedAt = OffsetDateTime.now().minusHours(2);
        OffsetDateTime generatedAt = OffsetDateTime.now().minusHours(1);
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("GRADED", gradedAt, OffsetDateTime.now())));
        when(reportRepository.findByAssignmentStudentId(ASSIGNMENT_STUDENT_ID))
                .thenReturn(Optional.of(readyReport(generatedAt)));
        when(reportRepository.startGeneration(anyLong(), any(), any())).thenReturn(1);

        AnalysisReportGenerationResponse response =
                service.requestGeneration(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID);

        assertThat(response.generationStatus()).isEqualTo(GenerationStatus.GENERATING);
        assertThat(response.retryAfterMs()).isEqualTo(3000L);
        verify(eventPublisher).publishEvent(any(AnalysisReportGenerationRequested.class));
    }

    @Test
    @DisplayName("이미 생성 중이면 작업을 새로 시작하지 않는다")
    void doesNotStartWhenAlreadyGenerating() {
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("GRADED", OffsetDateTime.now(), null)));
        when(reportRepository.findByAssignmentStudentId(ASSIGNMENT_STUDENT_ID))
                .thenReturn(Optional.empty());
        when(reportRepository.startGeneration(anyLong(), any(), any())).thenReturn(0);

        AnalysisReportGenerationResponse response =
                service.requestGeneration(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID);

        assertThat(response.generationStatus()).isEqualTo(GenerationStatus.GENERATING);
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("보고서를 만든 적이 없으면 404가 아니라 생성 전 상태로 응답한다")
    void returnsPendingWhenReportNeverGenerated() {
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("GRADED", OffsetDateTime.now(), null)));
        when(reportRepository.findByAssignmentStudentId(ASSIGNMENT_STUDENT_ID))
                .thenReturn(Optional.empty());

        AnalysisReportResponse response =
                service.getReport(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID);

        assertThat(response.generationStatus()).isEqualTo(GenerationStatus.PENDING);
        assertThat(response.summaryMessage()).isNull();
        assertThat(response.itemMessages()).isEmpty();
    }

    @Test
    @DisplayName("채점 전이어도 조회는 막지 않는다")
    void allowsReadBeforeGrading() {
        allowAssignment();
        when(queryRepository.findReportSource(ASSIGNMENT_ID, STUDENT_ID))
                .thenReturn(Optional.of(source("SUBMITTED", null, null)));
        when(reportRepository.findByAssignmentStudentId(ASSIGNMENT_STUDENT_ID))
                .thenReturn(Optional.empty());

        assertThat(service.getReport(TEACHER_ID, ASSIGNMENT_ID, STUDENT_ID).generationStatus())
                .isEqualTo(GenerationStatus.PENDING);
    }

    private void allowAssignment() {
        when(classQueryService.getAuthorizedAssignment(TEACHER_ID, ASSIGNMENT_ID))
                .thenReturn(new AnalysisAssignmentAccessRow(
                        ASSIGNMENT_ID, "학습지", WorksheetType.GENERAL_LEARNING,
                        "1반", TEACHER_ID, TEACHER_ID));
    }

    private AnalysisReportSourceRow source(
            String status,
            OffsetDateTime gradedAt,
            OffsetDateTime overriddenAt
    ) {
        return new AnalysisReportSourceRow(
                ASSIGNMENT_STUDENT_ID, status, gradedAt, overriddenAt);
    }

    private AnalysisReport readyReport(OffsetDateTime generatedAt) {
        AnalysisReport report = BeanUtils.instantiateClass(AnalysisReport.class);
        ReflectionTestUtils.setField(report, "id", 1L);
        ReflectionTestUtils.setField(report, "assignmentStudentId", ASSIGNMENT_STUDENT_ID);
        ReflectionTestUtils.setField(report, "generationStatus", GenerationStatus.READY);
        ReflectionTestUtils.setField(report, "summaryMessage", "요약");
        ReflectionTestUtils.setField(report, "overallObservation", "관찰");
        ReflectionTestUtils.setField(report, "generatedAt", generatedAt);
        return report;
    }
}
