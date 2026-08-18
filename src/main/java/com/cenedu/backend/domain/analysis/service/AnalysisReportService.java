package com.cenedu.backend.domain.analysis.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportGenerationResponse;
import com.cenedu.backend.domain.analysis.dto.response.AnalysisReportResponse;
import com.cenedu.backend.domain.analysis.entity.AnalysisReport;
import com.cenedu.backend.domain.analysis.entity.AnalysisReportItemMessage;
import com.cenedu.backend.domain.analysis.entity.enums.GenerationStatus;
import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportItemMessageRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportQueryRepository;
import com.cenedu.backend.domain.analysis.repository.AnalysisReportRepository;
import com.cenedu.backend.domain.analysis.repository.row.AnalysisReportSourceRow;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 학생 상세 화면의 AI 분석 문장 생성 요청과 조회를 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisReportService {

    /** 조회를 다시 시도하기까지 기다릴 시간. 생성이 20~40초 걸려 이 간격이면 10여 회로 끝난다. */
    private static final long RETRY_AFTER_MS = 3000L;

    /** 생성 중인 채로 이 시간을 넘긴 보고서는 작업이 죽은 것으로 보고 다시 맡는다. */
    private static final Duration STALE_GENERATING = Duration.ofMinutes(5);

    private final AnalysisClassQueryService classQueryService;
    private final AnalysisReportRepository reportRepository;
    private final AnalysisReportItemMessageRepository itemMessageRepository;
    private final AnalysisReportQueryRepository queryRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * AI 문장 생성을 요청한다. 이미 최신 문장이 있으면 다시 만들지 않는다.
     *
     * <p>화면에 들어올 때마다 호출해도 되도록 만들었다. 채점 결과가 그대로면 생성을 건너뛰고
     * 현재 상태만 돌려준다.
     */
    @Transactional
    public AnalysisReportGenerationResponse requestGeneration(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        AnalysisReportSourceRow source = requireGradedSource(teacherId, assignmentId, studentId);
        Optional<AnalysisReport> existing = reportRepository
                .findByAssignmentStudentId(source.assignmentStudentId());

        boolean upToDate = existing
                .filter(report -> report.getGenerationStatus() == GenerationStatus.READY)
                .filter(report -> !report.isStaleAgainst(source.lastGradingChangedAt()))
                .isPresent();
        if (upToDate) {
            return new AnalysisReportGenerationResponse(GenerationStatus.READY, 0L);
        }

        LocalDateTime now = LocalDateTime.now();
        int started = reportRepository.startGeneration(
                source.assignmentStudentId(), now, now.minus(STALE_GENERATING));
        if (started > 0) {
            eventPublisher.publishEvent(new AnalysisReportGenerationRequested(
                    assignmentId, source.assignmentStudentId()));
        }
        return new AnalysisReportGenerationResponse(
                GenerationStatus.GENERATING, RETRY_AFTER_MS);
    }

    /** 저장된 AI 문장을 반환한다. 아직 만든 적이 없으면 생성 전 상태로 응답한다. */
    public AnalysisReportResponse getReport(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        AnalysisReportSourceRow source = requireAssignedStudent(
                teacherId, assignmentId, studentId);
        return reportRepository.findByAssignmentStudentId(source.assignmentStudentId())
                .map(this::toResponse)
                .orElseGet(AnalysisReportResponse::notGenerated);
    }

    /**
     * 검증을 마친 문장을 저장하고 생성 완료로 바꾼다.
     *
     * <p>문항별 문장은 갱신하지 않고 지운 뒤 다시 넣는다. 이전 생성에는 있었지만 이번에는 빠진
     * 문항이 남지 않게 하려는 것이다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeGeneration(
            long assignmentStudentId,
            AnalysisReportDraft draft,
            List<AnalysisReportDraft.ItemMessageDraft> validatedItems
    ) {
        AnalysisReport report = reportRepository.findByAssignmentStudentId(assignmentStudentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED));

        itemMessageRepository.deleteByAnalysisReportId(report.getId());
        itemMessageRepository.flush();
        itemMessageRepository.saveAll(validatedItems.stream()
                .map(item -> AnalysisReportItemMessage.create(
                        report.getId(),
                        item.worksheetItemId(),
                        item.observation(),
                        item.learningPoint(),
                        item.retryGuide()))
                .toList());

        report.markReady(
                draft.summaryMessage(),
                draft.overallObservation(),
                draft.promptVersion(),
                draft.modelName(),
                draft.llmSchemaVersion(),
                OffsetDateTime.now());
    }

    /** 생성에 실패했음을 기록한다. 직전에 성공한 문장은 남긴다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failGeneration(long assignmentStudentId, String errorCode) {
        reportRepository.findByAssignmentStudentId(assignmentStudentId)
                .ifPresent(report -> report.markFailed(errorCode));
    }

    private AnalysisReportSourceRow requireGradedSource(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        AnalysisReportSourceRow source = requireAssignedStudent(
                teacherId, assignmentId, studentId);
        if (!source.isGraded()) {
            throw new BusinessException(ErrorCode.ANALYSIS_REPORT_NOT_GRADED);
        }
        return source;
    }

    private AnalysisReportSourceRow requireAssignedStudent(
            long teacherId,
            long assignmentId,
            long studentId
    ) {
        classQueryService.getAuthorizedAssignment(teacherId, assignmentId);
        return queryRepository.findReportSource(assignmentId, studentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.ANALYSIS_STUDENT_NOT_ASSIGNED));
    }

    private AnalysisReportResponse toResponse(AnalysisReport report) {
        List<AnalysisReportResponse.ItemMessage> itemMessages = itemMessageRepository
                .findByAnalysisReportIdOrderByWorksheetItemIdAsc(report.getId()).stream()
                .map(message -> new AnalysisReportResponse.ItemMessage(
                        message.getWorksheetItemId(),
                        message.getObservation(),
                        message.getLearningPoint(),
                        message.getRetryGuide()))
                .toList();
        return new AnalysisReportResponse(
                report.getGenerationStatus(),
                report.getSummaryMessage(),
                null,
                itemMessages,
                report.getOverallObservation());
    }
}
