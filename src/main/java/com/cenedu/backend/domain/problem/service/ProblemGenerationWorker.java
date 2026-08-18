package com.cenedu.backend.domain.problem.service;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationCommand;
import com.cenedu.backend.domain.problem.authoring.generation.ProblemGenerationWorkItem;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Item 하나만 실행하므로 호출부가 여러 Item을 병렬 실행해도 상태가 섞이지 않는 Worker다. */
@Component
public class ProblemGenerationWorker {

    private final ProblemGenerationJobService jobService;
    private final ProblemCandidateProcessingService candidateProcessingService;
    private final ObjectProvider<ProblemGenerationPort> generationPortProvider;

    public ProblemGenerationWorker(
            ProblemGenerationJobService jobService,
            ProblemCandidateProcessingService candidateProcessingService,
            ObjectProvider<ProblemGenerationPort> generationPortProvider
    ) {
        this.jobService = jobService;
        this.candidateProcessingService = candidateProcessingService;
        this.generationPortProvider = generationPortProvider;
    }

    /** 선점한 Item을 생성·검증하고 의미 실패 시 최대 두 번 같은 명령으로 재생성한다. */
    public void execute(Long itemId) {
        ProblemGenerationWorkItem workItem = jobService.claim(itemId);
        while (true) {
            ProblemCandidateDraft candidate;
            try {
                candidate = generationPort().generate(workItem.command());
                if (candidate == null || !workItem.command().requestId()
                        .equals(candidate.requestId())) {
                    throw new IllegalStateException(
                            "생성 결과의 requestId가 Item 명령과 다릅니다.");
                }
                jobService.startVerification(workItem.itemId());
            } catch (RuntimeException exception) {
                if (jobService.prepareRetry(workItem, "GENERATION_FAILED")) {
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
                if (jobService.prepareRetry(workItem, "CANDIDATE_INVALID")) {
                    continue;
                }
                jobService.fail(workItem, "CANDIDATE_INVALID");
                return;
            }

            if (result.promoted()) {
                jobService.succeed(workItem);
                return;
            }
            if (!jobService.prepareRetry(workItem, result.status().name())) {
                jobService.fail(workItem, result.status().name());
                return;
            }
        }
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
                command.curriculumContext(),
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
