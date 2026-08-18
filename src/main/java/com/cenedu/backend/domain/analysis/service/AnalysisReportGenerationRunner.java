package com.cenedu.backend.domain.analysis.service;

import java.util.List;

import com.cenedu.backend.domain.analysis.report.AnalysisReportDraft;
import com.cenedu.backend.domain.analysis.report.AnalysisReportGenerationPort;
import com.cenedu.backend.domain.analysis.report.AnalysisReportRequest;
import com.cenedu.backend.domain.analysis.service.AnalysisReportDraftValidator
        .AnalysisReportDraftInvalidException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 보고서 생성 작업을 실행한다.
 *
 * <p>요청 트랜잭션이 <b>커밋된 뒤에</b> 실행기에 작업을 넘긴다. 커밋 전에 넘기면 작업 스레드가 아직
 * 보이지 않는 행을 읽거나, 요청이 롤백됐는데 LLM 호출만 나간다.
 *
 * <p>실행기가 포화되어 작업을 거절하면 보고서를 생성 실패로 되돌린다. 그러지 않으면 행이 생성 중인
 * 채로 남아 교사 화면이 몇 분 동안 "생성 중"에 머문다.
 */
@Component
public class AnalysisReportGenerationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(AnalysisReportGenerationRunner.class);

    private static final String ASYNC_REJECTED = "ASYNC_REJECTED";
    private static final String GENERATION_FAILED = "GENERATION_FAILED";

    private final ThreadPoolTaskExecutor executor;
    private final AnalysisReportRequestAssembler requestAssembler;
    private final AnalysisReportGenerationPort generationPort;
    private final AnalysisReportDraftValidator draftValidator;
    private final AnalysisReportService reportService;

    public AnalysisReportGenerationRunner(
            @Qualifier("analysisReportTaskExecutor") ThreadPoolTaskExecutor executor,
            AnalysisReportRequestAssembler requestAssembler,
            AnalysisReportGenerationPort generationPort,
            AnalysisReportDraftValidator draftValidator,
            AnalysisReportService reportService
    ) {
        this.executor = executor;
        this.requestAssembler = requestAssembler;
        this.generationPort = generationPort;
        this.draftValidator = draftValidator;
        this.reportService = reportService;
    }

    /** 생성 상태 전이가 커밋되면 작업을 실행기에 넘긴다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onGenerationRequested(AnalysisReportGenerationRequested event) {
        try {
            executor.execute(() -> generate(event));
        } catch (TaskRejectedException e) {
            log.warn("분석 보고서 생성 작업이 거절됨 — assignmentStudentId={}",
                    event.assignmentStudentId());
            reportService.failGeneration(event.assignmentStudentId(), ASYNC_REJECTED);
        }
    }

    private void generate(AnalysisReportGenerationRequested event) {
        long assignmentStudentId = event.assignmentStudentId();
        try {
            AnalysisReportRequest request = requestAssembler.assemble(
                    event.assignmentId(), event.studentId(), assignmentStudentId);
            AnalysisReportDraft draft = generationPort.generate(request);
            List<AnalysisReportDraft.ItemMessageDraft> validated =
                    draftValidator.validate(draft, request.gradedWorksheetItemIds());
            reportService.completeGeneration(assignmentStudentId, draft, validated);
            log.info("분석 보고서 생성 완료 — assignmentStudentId={}, 문항 {}건",
                    assignmentStudentId, validated.size());
        } catch (AnalysisReportDraftInvalidException e) {
            log.warn("분석 보고서 문장 검증 실패 — assignmentStudentId={}, code={}",
                    assignmentStudentId, e.getErrorCode());
            reportService.failGeneration(assignmentStudentId, e.getErrorCode());
        } catch (RuntimeException e) {
            log.error("분석 보고서 생성 실패 — assignmentStudentId={}", assignmentStudentId, e);
            reportService.failGeneration(assignmentStudentId, GENERATION_FAILED);
        }
    }
}
