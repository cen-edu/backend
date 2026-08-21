package com.cenedu.backend.domain.problem.service;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAiExecutionBudgetPort;
import com.cenedu.backend.domain.problem.authoring.verification.*;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 확정된 수정 후보를 생성·검증하고 PASSED일 때만 current로 승격한다. */
@Component
public class ProblemModificationWorker {
    private final ObjectProvider<ProblemModificationPort> modificationPortProvider;
    private final ProblemCandidateProcessingService processingService;
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringStateService stateService;
    private final ProblemAiExecutionBudgetPort executionBudgetPort;

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemModificationWorker(ObjectProvider<ProblemModificationPort> modificationPortProvider,
            ProblemCandidateProcessingService processingService,
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringStateService stateService) {
        this(modificationPortProvider, processingService, sessionRepository, stateService, null);
    }

    public ProblemModificationWorker(ObjectProvider<ProblemModificationPort> modificationPortProvider,
            ProblemCandidateProcessingService processingService,
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringStateService stateService,
            ProblemAiExecutionBudgetPort executionBudgetPort) {
        this.modificationPortProvider = modificationPortProvider;
        this.processingService = processingService;
        this.sessionRepository = sessionRepository;
        this.stateService = stateService;
        this.executionBudgetPort = executionBudgetPort;
    }

    /** 수정 후보를 AI_MODIFY Version으로 등록하고 의미 실패 시 최대 두 번 새 후보로 재시도한다. */
    public CandidateProcessingResult execute(long teacherId, ProblemModificationCommand command) {
        ProblemModificationPort port = modificationPortProvider.getIfAvailable();
        if (port == null) throw new BusinessException(ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
        ProblemEditExecutionPlan plan = command.plan();
        ProblemAiExecutionBudgetPort.Scope budget = executionBudgetPort == null ? null
                : executionBudgetPort.open(command.requestId().toString(), Long.toString(plan.baseVersionId()),
                        Long.toString(plan.sessionId()), "MODIFICATION");
        CandidateProcessingResult lastResult = null;
        try {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (budget != null) budget.stage(ProblemAiExecutionBudgetPort.Stage.MODIFICATION, attempt + 1);
            ProblemModificationCommand attemptCommand = commandForAttempt(command, attempt);
            ProblemCandidateDraft candidate;
            try {
                candidate = port.modify(attemptCommand);
            } catch (RuntimeException exception) {
                if (attempt < 2) continue;
                stateService.failOperation(teacherId, plan.sessionId(), "MODIFICATION_FAILED");
                throw exception;
            }
            lastResult = processingService.process(processingRequest(
                    teacherId, plan, command, candidate));
            if (lastResult.promoted()
                    || lastResult.status() == VerificationOverallStatus.ERROR
                    || attempt == 1) {
                return lastResult;
            }
            stateService.prepareModificationRetry(
                    teacherId, plan.sessionId(), lastResult.status().name());
        }
        return lastResult;
        } finally {
            if (budget != null) budget.close();
        }
    }

    private CandidateProcessingRequest processingRequest(
            long teacherId,
            ProblemEditExecutionPlan plan,
            ProblemModificationCommand command,
            ProblemCandidateDraft candidate
    ) {
        return new CandidateProcessingRequest(
                teacherId, plan.sessionId(), plan.baseVersionId(), AuthoringOperationType.AI_MODIFY,
                VerificationOperationType.EDIT, candidate,
                new VerificationExpectation(candidate.snapshot().metadata().questionType(),
                        candidate.snapshot().metadata().difficulty(), null,
                        candidate.snapshot().metadata().evaluationArea(), java.util.List.of(),
                        candidate.snapshot().assets().stream().map(a -> a.assetKey()).toList()),
                new EditVerificationContext(command.baseSnapshot(), plan.instructions(),
                        plan.requestedTargets(), plan.dependentTargets(), plan.protectedTargets()),
                "확정된 교사 수정 실행");
    }

    /** Version의 sourceRequestId 유일성을 지키면서 같은 확정 계획을 재실행한다. */
    private ProblemModificationCommand commandForAttempt(
            ProblemModificationCommand command,
            int attempt
    ) {
        if (attempt == 0) return command;
        UUID requestId = UUID.nameUUIDFromBytes(
                (command.requestId() + ":attempt:" + attempt)
                        .getBytes(StandardCharsets.UTF_8));
        return new ProblemModificationCommand(
                requestId, command.plan(), command.baseSnapshot(), command.baseSemanticModel());
    }

    /** 수정 대상 Session이 실제로 교사 소유인지 확인한다. */
    public void requireOwned(long teacherId, long sessionId) {
        sessionRepository.findByIdAndOwnerTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
    }
}
