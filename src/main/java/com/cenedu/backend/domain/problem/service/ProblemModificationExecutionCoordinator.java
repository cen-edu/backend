package com.cenedu.backend.domain.problem.service;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemModificationExecutionResult;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 확정 수정 계획을 RESTORE 또는 AI 수정 실행으로 분기한다. */
@Component
public class ProblemModificationExecutionCoordinator {
    private final ProblemModificationWorker modificationWorker;
    private final ProblemAuthoringStateService stateService;
    private final ProblemQuestionSelector questionSelector;
    private final ProblemBankSnapshotQueryService bankSnapshotQueryService;
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final TransactionTemplate transactionTemplate;
    private ProblemSemanticModificationService semanticModificationService;
    private ProblemStructuralRegenerationService structuralRegenerationService;
    private ProblemTeacherDecisionEventService decisionEventService;

    public ProblemModificationExecutionCoordinator(ProblemModificationWorker modificationWorker,
            ProblemAuthoringStateService stateService, ProblemQuestionSelector questionSelector,
            ProblemBankSnapshotQueryService bankSnapshotQueryService,
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository, ProblemAuthoringJsonCodec jsonCodec,
            PlatformTransactionManager transactionManager) {
        this.modificationWorker = modificationWorker;
        this.stateService = stateService;
        this.questionSelector = questionSelector;
        this.bankSnapshotQueryService = bankSnapshotQueryService;
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.jsonCodec = jsonCodec;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** 교사 결정 이벤트 기록기를 선택적으로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setDecisionEventService(ProblemTeacherDecisionEventService service) { this.decisionEventService = service; }

    /** semantic patch 실행기를 선택적으로 연결해 기존 legacy 경로와 공존시킨다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSemanticModificationService(ProblemSemanticModificationService service) {
        this.semanticModificationService = service;
    }

    /** 구조적 semantic patch를 generation port 경로로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setStructuralRegenerationService(ProblemStructuralRegenerationService service) {
        this.structuralRegenerationService = service;
    }

    /** RESTORE는 AI 호출 없이 즉시 전환하고 나머지는 수정 Worker에 위임한다. */
    public Object execute(long teacherId, ProblemEditExecutionPlan plan,
                          com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        if (plan.action() == EditAction.RESTORE) {
            stateService.restorePassedVersion(teacherId, plan.sessionId(), plan.restoreVersionId());
            if (decisionEventService != null) decisionEventService.recordRestore(
                    teacherId, plan.sessionId(), plan.restoreVersionId(), plan.requestId());
            return new com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemModificationExecutionResult(
                    plan.restoreVersionId(), com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.RESTORE,
                    new com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticDiff(java.util.List.of(),
                            java.util.EnumSet.allOf(com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticImpactArea.class), false, false),
                    true, false);
        }
        if (plan.semanticPatch() != null) {
            ProblemAuthoringVersion baseVersion = versionRepository
                    .findByIdAndSessionId(plan.baseVersionId(), plan.sessionId())
                    .orElseThrow(() -> new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
            if (plan.semanticPatch().mode() == com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.STRUCTURAL_REGENERATION) {
                if (structuralRegenerationService == null)
                    throw new com.cenedu.backend.global.common.BusinessException(
                            com.cenedu.backend.global.common.ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
                if (baseVersion.getSemanticModel() == null)
                    throw new com.cenedu.backend.global.common.BusinessException(
                            com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
                var baseModel = new com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec(
                        new tools.jackson.databind.ObjectMapper()).readSemanticModel(baseVersion.getSemanticModel());
                return structuralRegenerationService.regenerate(teacherId, baseVersion, plan, baseModel);
            }
            if (baseVersion.getSemanticModel() == null) {
                if (!plan.instructions().isEmpty()) {
                    Object fallback = modificationWorker.execute(teacherId,
                            new com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand(
                                    plan.requestId(), plan, baseSnapshot, null));
                    return fallback;
                }
                throw new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
            }
            if (semanticModificationService == null)
                throw new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
            return semanticModificationService.apply(teacherId, plan.sessionId(), baseVersion, plan.semanticPatch());
        }
        if (plan.action() == EditAction.REPLACE
                && plan.sourcePolicy() == ReplacementSourcePolicy.BANK_FIRST) {
            Object bankResult = transactionTemplate.execute(status -> tryBankReuse(teacherId, plan, baseSnapshot));
            if (bankResult != null) {
                if (decisionEventService != null) decisionEventService.recordReplacement(
                        teacherId, plan.sessionId(), plan.baseVersionId(), plan.requestId(), plan.instructions());
                return bankResult;
            }
        }
        Object result = modificationWorker.execute(teacherId,
                new com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand(
                        plan.requestId(), plan, baseSnapshot, null));
        if (plan.action() == EditAction.REPLACE && decisionEventService != null) decisionEventService.recordReplacement(
                teacherId, plan.sessionId(), plan.baseVersionId(), plan.requestId(), plan.instructions());
        return result;
    }

    private Long tryBankReuse(long teacherId, ProblemEditExecutionPlan plan,
                              com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        var requested = plan.requestedSpecification();
        var type = requested == null || requested.questionType() == null
                ? baseSnapshot.metadata().questionType() : requested.questionType();
        String difficultyValue = requested == null || requested.difficulty() == null
                ? baseSnapshot.metadata().difficulty() : requested.difficulty();
        var candidates = questionSelector.selectAvailable(baseSnapshot.metadata().subUnitId(),
                difficulty(difficultyValue), type, 1, java.util.Set.of());
        if (candidates.isEmpty()) return null;
        var bank = bankSnapshotQueryService.getSnapshots(List.of(candidates.getFirst().getId())).getFirst();
        if (!bank.reusable()) return null;
        var session = sessionRepository.findOwnedByIdForUpdate(plan.sessionId(), teacherId)
                .orElseThrow(() -> new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        int versionNo = versionRepository.findFirstBySessionIdOrderByVersionNoDesc(plan.sessionId())
                .map(previous -> previous.getVersionNo() + 1).orElse(1);
        ProblemAuthoringVersion version = versionRepository.save(ProblemAuthoringVersion.create(
                plan.sessionId(), versionNo, plan.baseVersionId(), plan.requestId(),
                AuthoringOperationType.BANK_REUSE, bank.questionId(), 1,
                jsonCodec.write(bank.snapshot()), "{}", "문제은행 교체"));
        version.startVerification(java.util.UUID.nameUUIDFromBytes(
                ("bank-edit:" + plan.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        version.passVerification("{\"source\":\"BANK_REUSE\"}");
        session.attachPendingVersion(version.getId());
        session.promotePendingVersion(version.getId(), version.getVerificationStatus());
        return version.getId();
    }

    private short difficulty(String value) {
        return switch (value) { case "low" -> 1; case "mid" -> 2; case "high" -> 3;
            default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다."); };
    }
}
