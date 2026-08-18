package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.port.ProblemModificationPort;
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

    public ProblemModificationWorker(ObjectProvider<ProblemModificationPort> modificationPortProvider,
            ProblemCandidateProcessingService processingService,
            ProblemAuthoringSessionRepository sessionRepository) {
        this.modificationPortProvider = modificationPortProvider;
        this.processingService = processingService;
        this.sessionRepository = sessionRepository;
    }

    /** 수정 후보를 AI_MODIFY Version으로 등록하고 기존 current는 검증 전까지 유지한다. */
    public CandidateProcessingResult execute(long teacherId, ProblemModificationCommand command) {
        ProblemModificationPort port = modificationPortProvider.getIfAvailable();
        if (port == null) throw new BusinessException(ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
        ProblemCandidateDraft candidate = port.modify(command);
        ProblemEditExecutionPlan plan = command.plan();
        return processingService.process(new CandidateProcessingRequest(
                teacherId, plan.sessionId(), plan.baseVersionId(), AuthoringOperationType.AI_MODIFY,
                VerificationOperationType.EDIT, candidate,
                new VerificationExpectation(candidate.snapshot().metadata().questionType(),
                        candidate.snapshot().metadata().difficulty(), null,
                        candidate.snapshot().metadata().evaluationArea(), java.util.List.of(),
                        candidate.snapshot().assets().stream().map(a -> a.assetKey()).toList()),
                new EditVerificationContext(command.baseSnapshot(), plan.instructions(),
                        plan.requestedTargets(), plan.dependentTargets(), plan.protectedTargets()),
                "확정된 교사 수정 실행"));
    }

    /** 수정 대상 Session이 실제로 교사 소유인지 확인한다. */
    public void requireOwned(long teacherId, long sessionId) {
        sessionRepository.findByIdAndOwnerTeacherId(sessionId, teacherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
    }
}
