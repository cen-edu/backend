package com.cenedu.backend.domain.problem.service;

import java.util.List;
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
        int attempt = 0;
        while (true) {
            ProblemGenerationCommand attemptCommand = commandForAttempt(workItem.command(), attempt);
            if (semanticReferenceEnricher != null) attemptCommand = semanticReferenceEnricher.enrich(attemptCommand);
            ProblemCandidateDraft candidate;
            try (ProblemAiConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
                candidate = generationPort().generate(attemptCommand);
                if (candidate == null || !attemptCommand.requestId()
                        .equals(candidate.requestId())) {
                    throw new IllegalStateException(
                            "생성 결과의 requestId가 Item 명령과 다릅니다.");
                }
                jobService.startVerification(workItem.itemId());
            } catch (RuntimeException exception) {
                log.warn("문제 생성 단계 실패 — itemId={}, attempt={}, errorType={}, message={}",
                        workItem.itemId(), attempt, exception.getClass().getSimpleName(),
                        safeMessage(exception));
                if (jobService.prepareRetry(workItem, "GENERATION_FAILED")) {
                    attempt++;
                    continue;
                }
                jobService.fail(workItem, "GENERATION_FAILED");
                return;
            }

            CandidateProcessingResult result;
            try {
                result = candidateProcessingService.process(
                        processingRequest(workItem, candidate));
            } catch (RuntimeException exception) {
                log.warn("문제 후보 처리 실패 — itemId={}, attempt={}, errorType={}, message={}",
                        workItem.itemId(), attempt, exception.getClass().getSimpleName(),
                        safeMessage(exception));
                if (jobService.prepareRetry(workItem, "CANDIDATE_INVALID")) {
                    attempt++;
                    continue;
                }
                jobService.fail(workItem, "CANDIDATE_INVALID");
                return;
            }

            if (result.promoted()) {
                linkAuthoringVersion(workItem.command(), result.versionId());
                jobService.succeed(workItem);
                return;
            }
            if (result.status() == com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus.ERROR) {
                jobService.fail(workItem, "VERIFICATION_ERROR");
                return;
            }
            if (!jobService.prepareRetry(workItem, result.status().name())) {
                jobService.fail(workItem, result.status().name());
                return;
            }
            attempt++;
        }
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
                command.specification(), command.curriculum(), command.references(), command.conceptEvidence());
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return "(no-message)";
        return message.replaceAll("\\s+", " ");
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
