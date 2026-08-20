package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemRetrievalTracePort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** Item 하나만 실행하므로 호출부가 여러 Item을 병렬 실행해도 상태가 섞이지 않는 Worker다. */
@Component
public class ProblemGenerationWorker {
    private static final Logger log = LoggerFactory.getLogger(ProblemGenerationWorker.class);

    private final ProblemGenerationJobService jobService;
    private final ProblemCandidateProcessingService candidateProcessingService;
    private final ObjectProvider<ProblemGenerationPort> generationPortProvider;
    private final ProblemAiConcurrencyLimiter concurrencyLimiter;
    private final ObjectProvider<ProblemRetrievalTracePort> tracePort;
    private final ProblemSemanticReferenceEnricher semanticReferenceEnricher;

    public ProblemGenerationWorker(
            ProblemGenerationJobService jobService,
            ProblemCandidateProcessingService candidateProcessingService,
            ObjectProvider<ProblemGenerationPort> generationPortProvider,
            ProblemAiConcurrencyLimiter concurrencyLimiter
    ) {
        this(jobService, candidateProcessingService, generationPortProvider, concurrencyLimiter, null, null);
    }

    /** retrieval trace 연결 Port를 선택적으로 주입한다. */
    @org.springframework.beans.factory.annotation.Autowired
    public ProblemGenerationWorker(
            ProblemGenerationJobService jobService,
            ProblemCandidateProcessingService candidateProcessingService,
            ObjectProvider<ProblemGenerationPort> generationPortProvider,
            ProblemAiConcurrencyLimiter concurrencyLimiter,
            ObjectProvider<ProblemRetrievalTracePort> tracePort,
            ProblemSemanticReferenceEnricher semanticReferenceEnricher
    ) {
        this.jobService = jobService;
        this.candidateProcessingService = candidateProcessingService;
        this.generationPortProvider = generationPortProvider;
        this.concurrencyLimiter = concurrencyLimiter;
        this.tracePort = tracePort;
        this.semanticReferenceEnricher = semanticReferenceEnricher;
    }

    /** 선점한 Item을 생성·검증하고 의미 실패 시 최대 두 번 같은 명령으로 재생성한다. */
    public void execute(Long itemId) {
        ProblemGenerationWorkItem workItem = jobService.tryClaim(itemId).orElse(null);
        if (workItem == null) {
            return;
        }
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        putItemContext(workItem);
        try {
            int attempt = 0;
            while (true) {
                MDC.put("candidateAttempt", Integer.toString(attempt + 1));
                ProblemGenerationCommand attemptCommand = commandForAttempt(workItem.command(), attempt);
                ProblemCandidateDraft candidate;
                try {
                    if (semanticReferenceEnricher != null) {
                        ProblemGenerationCommand commandBeforeEnrichment = attemptCommand;
                        var enrichment = runStage("ENRICHMENT",
                                () -> semanticReferenceEnricher.enrichWithStatus(commandBeforeEnrichment));
                        attemptCommand = enrichment.command();
                    }
                    ProblemGenerationCommand commandForGeneration = attemptCommand;
                    candidate = runStage("GENERATION", () -> {
                        try (ProblemAiConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
                            return generationPort().generate(commandForGeneration);
                        }
                    });
                    if (candidate == null || !attemptCommand.requestId()
                            .equals(candidate.requestId())) {
                        throw new IllegalStateException(
                                "생성 결과의 requestId가 Item 명령과 다릅니다.");
                    }
                    jobService.startVerification(workItem.itemId());
                } catch (RuntimeException exception) {
                    log.warn("event=problem_authoring_stage operation=GENERATION outcome=ERROR itemId={} attempt={} errorType={}",
                            workItem.itemId(), attempt + 1, exception.getClass().getSimpleName());
                    if (jobService.prepareRetry(workItem, "GENERATION_FAILED")) {
                        attempt++;
                        continue;
                    }
                    jobService.fail(workItem, "GENERATION_FAILED");
                    logItemOutcome(workItem, "FAILED", "GENERATION_FAILED");
                    return;
                }

                CandidateProcessingResult result;
                try {
                    result = runStage("VERIFICATION", () -> candidateProcessingService.process(
                            processingRequest(workItem, candidate)));
                } catch (RuntimeException exception) {
                    log.warn("event=problem_authoring_stage operation=GENERATION stage=VERIFICATION outcome=ERROR itemId={} attempt={} errorType={}",
                            workItem.itemId(), attempt + 1, exception.getClass().getSimpleName());
                    if (jobService.prepareRetry(workItem, "CANDIDATE_INVALID")) {
                        attempt++;
                        continue;
                    }
                    jobService.fail(workItem, "CANDIDATE_INVALID");
                    logItemOutcome(workItem, "FAILED", "CANDIDATE_INVALID");
                    return;
                }

                if (result.promoted()) {
                    runStage("PROMOTION", () -> {
                        linkAuthoringVersion(workItem.command(), result.versionId());
                        jobService.succeed(workItem);
                        return null;
                    });
                    logItemOutcome(workItem, "SUCCEEDED", "PASSED");
                    return;
                }
                if (result.status() == com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus.ERROR) {
                    jobService.fail(workItem, "VERIFICATION_ERROR");
                    logItemOutcome(workItem, "FAILED", "VERIFICATION_ERROR");
                    return;
                }
                if (!jobService.prepareRetry(workItem, result.status().name())) {
                    jobService.fail(workItem, result.status().name());
                    logItemOutcome(workItem, "FAILED", result.status().name());
                    return;
                }
                attempt++;
            }
        } finally {
            restoreContext(previousContext);
        }
    }

    /** Item 식별자를 MDC에 넣어 비동기 생성·RAG·LLM 로그를 하나의 문항으로 묶는다. */
    private void putItemContext(ProblemGenerationWorkItem workItem) {
        if (MDC.get("traceId") == null) {
            MDC.put("traceId", UUID.randomUUID().toString().substring(0, 8));
        }
        MDC.put("jobId", Long.toString(workItem.jobId()));
        MDC.put("itemId", Long.toString(workItem.itemId()));
        MDC.put("sessionId", Long.toString(workItem.sessionId()));
        MDC.put("operationId", workItem.command().requestId().toString());
        MDC.put("requestId", workItem.command().requestId().toString());
        MDC.put("purpose", workItem.command().purpose().name());
        log.info("event=problem_authoring_item stage=START outcome=STARTED itemId={} jobId={} sessionId={} purpose={}",
                workItem.itemId(), workItem.jobId(), workItem.sessionId(), workItem.command().purpose());
    }

    /** Item 종료 결과를 본문 없이 기록한다. */
    private void logItemOutcome(ProblemGenerationWorkItem workItem, String outcome, String reason) {
        log.info("event=problem_authoring_item stage=END outcome={} itemId={} jobId={} sessionId={} reason={}",
                outcome, workItem.itemId(), workItem.jobId(), workItem.sessionId(), reason);
    }

    /** 한 단계의 경과 시간을 기록하고 단계 결과를 호출부에 반환한다. */
    private <T> T runStage(String stage, StageCall<T> call) {
        MDC.put("stage", stage);
        long startedAt = System.nanoTime();
        try {
            T result = call.call();
            log.info("event=problem_authoring_stage operation=GENERATION stage={} outcome=SUCCESS elapsedMs={} "
                            + "jobId={} itemId={} sessionId={} operationId={} purpose={} candidateAttempt={}",
                    stage, elapsedMs(startedAt), context("jobId"), context("itemId"), context("sessionId"),
                    context("operationId"), context("purpose"), context("candidateAttempt"));
            return result;
        } catch (RuntimeException exception) {
            log.warn("event=problem_authoring_stage operation=GENERATION stage={} outcome=ERROR elapsedMs={} "
                            + "jobId={} itemId={} sessionId={} operationId={} purpose={} candidateAttempt={} errorType={}",
                    stage, elapsedMs(startedAt), context("jobId"), context("itemId"), context("sessionId"),
                    context("operationId"), context("purpose"), context("candidateAttempt"),
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    private String context(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    private void restoreContext(Map<String, String> previousContext) {
        if (previousContext == null || previousContext.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(previousContext);
        }
    }

    @FunctionalInterface
    private interface StageCall<T> {
        T call();
    }

    private void linkAuthoringVersion(ProblemGenerationCommand command, Long versionId) {
        if (command.retrievalRequestId() == null || versionId == null || tracePort == null) return;
        ProblemRetrievalTracePort trace = tracePort.getIfAvailable();
        if (trace == null) return;
        try { trace.linkAuthoringVersion(command.retrievalRequestId(), versionId); }
        catch (RuntimeException exception) { log.debug("검색 trace version 연결 실패 — errorType={}", exception.getClass().getSimpleName()); }
    }

    /** 첫 시도는 원 요청 ID를 유지하고 재생성 후보에는 결정적인 별도 ID를 부여한다. */
    private ProblemGenerationCommand commandForAttempt(ProblemGenerationCommand command, int attempt) {
        if (attempt == 0) return command;
        UUID attemptRequestId = UUID.nameUUIDFromBytes(
                (command.requestId() + ":attempt:" + attempt).getBytes(StandardCharsets.UTF_8));
        return new ProblemGenerationCommand(attemptRequestId, command.retrievalRequestId(), command.purpose(),
                command.specification(), command.curriculum(), command.references(), command.conceptEvidence(),
                command.personalizedEvidence());
    }

    private CandidateProcessingRequest processingRequest(
            ProblemGenerationWorkItem workItem,
            ProblemCandidateDraft candidate
    ) {
        ProblemGenerationCommand command = workItem.command();
        List<String> requiredAssetKeys = candidate.snapshot().assets().stream()
                .map(asset -> asset.assetKey())
                .toList();
        VerificationExpectation expectation = new VerificationExpectation(
                command.specification().questionType(),
                command.specification().difficulty(),
                command.curriculum(),
                command.specification().targetEvaluationArea(),
                command.specification().targetDiagnosticTypes(),
                requiredAssetKeys);
        return new CandidateProcessingRequest(
                workItem.ownerTeacherId(),
                workItem.sessionId(),
                null,
                AuthoringOperationType.AI_GENERATE,
                VerificationOperationType.CREATE,
                candidate,
                expectation,
                new com.cenedu.backend.domain.problem.authoring.verification.GenerationVerificationContext(
                        command.purpose(), command.references()),
                "AI 문항 생성");
    }

    private ProblemGenerationPort generationPort() {
        ProblemGenerationPort port = generationPortProvider.getIfAvailable();
        if (port == null) {
            throw new BusinessException(ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
        }
        return port;
    }
}
