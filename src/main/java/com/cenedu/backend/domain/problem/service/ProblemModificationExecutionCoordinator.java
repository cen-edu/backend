package com.cenedu.backend.domain.problem.service;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemModificationExecutionResult;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationPurpose;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReference;
import com.cenedu.backend.domain.problem.authoring.generation.GenerationReferenceRole;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceQuery;
import com.cenedu.backend.domain.problem.authoring.retrieval.ProblemReferenceRetrievalPort;
import com.cenedu.backend.domain.problem.config.ProblemRagProperties;
import com.cenedu.backend.domain.curriculum.service.CurriculumUnitQueryService;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 확정 수정 계획을 RESTORE 또는 AI 수정 실행으로 분기한다. */
@Component
public class ProblemModificationExecutionCoordinator {
    private static final Logger log = LoggerFactory.getLogger(ProblemModificationExecutionCoordinator.class);
    /** 구조 검증·자료 조건에 걸리는 후보가 있어도 교체가 성사되도록 확보하는 후보 수다. */
    private static final int BANK_CANDIDATE_LIMIT = 8;

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
    private ProblemSemanticExtractionService semanticExtractionService;
    private CurriculumUnitQueryService curriculumUnitQueryService;
    private ProblemTeacherDecisionEventService decisionEventService;
    private com.cenedu.backend.domain.problem.authoring.edit.ReplacementExclusionPort replacementExclusionPort;
    private ProblemReferenceRetrievalPort referenceRetrievalPort;
    private ProblemRagProperties ragProperties;

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

    /** 학습지처럼 Session 바깥 문맥이 강제하는 교체 후보 제외 규칙을 선택적으로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReplacementExclusionPort(
            com.cenedu.backend.domain.problem.authoring.edit.ReplacementExclusionPort port) {
        this.replacementExclusionPort = port;
    }

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

    /** semantic model이 없는 기존 Version의 lazy extraction 경계를 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSemanticExtractionService(ProblemSemanticExtractionService service) {
        this.semanticExtractionService = service;
    }

    /** extraction에 현재 Snapshot의 소단원 curriculum scope를 제공한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setCurriculumUnitQueryService(CurriculumUnitQueryService service) {
        this.curriculumUnitQueryService = service;
    }

    /** 은행 교체 미스 후 생성에 쓸 유사 문항 검색기를 선택적으로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setReferenceRetrievalPort(ProblemReferenceRetrievalPort port) {
        this.referenceRetrievalPort = port;
    }

    /** RAG 활성화와 후보 수 정책을 선택적으로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRagProperties(ProblemRagProperties properties) {
        this.ragProperties = properties;
    }

    /**
     * 확정 계획을 실행하고, 실패하면 Session을 재시도 가능한 상태로 되돌린 뒤 원인을 그대로 올린다.
     *
     * <p>ProblemEditConversationService.confirm의 activateEdit이 이미 별도 transaction에서
     * operationStatus=MODIFYING을 커밋한 뒤에 이 실행이 시작된다. 여기서 예외가 나가면
     * 그 MODIFYING이 그대로 남아, 같은 Session의 다음 수정 턴이 startCollecting의
     * requireDraftIdle에 막혀 전부 실패한다(한 번 실패한 Session이 영구히 죽는다).
     * 회수 자체가 또 실패해도 원래 원인을 가리지 않도록 suppressed로만 덧붙인다.
     */
    public Object execute(long teacherId, ProblemEditExecutionPlan plan,
                          com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        try {
            return doExecute(teacherId, plan, baseSnapshot);
        } catch (RuntimeException failure) {
            try {
                stateService.abortActiveExecution(teacherId, plan.sessionId(), "MODIFICATION_FAILED");
            } catch (RuntimeException recoveryFailure) {
                failure.addSuppressed(recoveryFailure);
            }
            throw failure;
        }
    }

    /** RESTORE는 AI 호출 없이 즉시 전환하고 나머지는 수정 Worker에 위임한다. */
    private Object doExecute(long teacherId, ProblemEditExecutionPlan plan,
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
        // 문제은행 조회 교체는 AI 호출 없이 끝나므로 semantic patch 실행보다 먼저 시도한다.
        // ProblemEditPolicy.effectivePatch가 교체 요청의 patch를 항상 STRUCTURAL_REGENERATION으로
        // 정규화하므로, 이 검사가 아래에 있으면 semanticPatch 분기가 항상 먼저 return해
        // BANK_FIRST 경로에 영원히 도달하지 못한다.
        if (plan.action() == EditAction.REPLACE
                && plan.sourcePolicy() == ReplacementSourcePolicy.BANK_FIRST) {
            ProblemModificationExecutionResult bankResult =
                    transactionTemplate.execute(status -> tryBankReuse(teacherId, plan, baseSnapshot));
            if (bankResult != null) {
                if (decisionEventService != null) decisionEventService.recordReplacement(
                        teacherId, plan.sessionId(), plan.baseVersionId(), plan.requestId(), plan.instructions());
                return bankResult;
            }
        }
        ProblemAuthoringVersion executionBaseVersion = versionRepository
                .findByIdAndSessionId(plan.baseVersionId(), plan.sessionId())
                .orElseThrow(() -> new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        CurriculumScope referenceCurriculum = referenceCurriculum(baseSnapshot, executionBaseVersion);
        List<GenerationReference> replacementReferences = replacementReferences(
                plan, baseSnapshot, executionBaseVersion, referenceCurriculum);
        if (plan.semanticPatch() != null) {
            ProblemAuthoringVersion baseVersion = executionBaseVersion;
            if (baseVersion.getSemanticModel() == null && semanticExtractionService != null) {
                var extraction = baseVersion.getSourceQuestionId() != null
                        ? semanticExtractionService.ensureVersionSemantic(
                                teacherId, plan.sessionId(), baseVersion.getId(), currentCurriculum(baseSnapshot))
                        : extractFinalizedQuestionSemantic(teacherId, plan, baseSnapshot);
                if (extraction.status() == SemanticExtractionStatus.EXTRACTED) {
                    baseVersion = versionRepository.findByIdAndSessionId(plan.baseVersionId(), plan.sessionId())
                            .orElseThrow(() -> new com.cenedu.backend.global.common.BusinessException(
                                    com.cenedu.backend.global.common.ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
                } else if (plan.instructions() != null && !plan.instructions().isEmpty()) {
                    Object fallback = modificationWorker.execute(teacherId,
                            modificationCommand(plan, baseSnapshot, baseVersion,
                                    referenceCurriculum, replacementReferences));
                    return legacyFallbackResult(plan, fallback);
                }
            }
            if (baseVersion.getSemanticModel() == null) {
                Object fallback = modificationWorker.execute(teacherId,
                        modificationCommand(plan, baseSnapshot, baseVersion,
                                referenceCurriculum, replacementReferences));
                return legacyFallbackResult(plan, fallback);
            }
            if (plan.semanticPatch().mode() == com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.STRUCTURAL_REGENERATION) {
                if (structuralRegenerationService == null)
                    throw new com.cenedu.backend.global.common.BusinessException(
                            com.cenedu.backend.global.common.ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
                if (baseVersion.getSemanticModel() == null)
                    throw new com.cenedu.backend.global.common.BusinessException(
                            com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
                var baseModel = new com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec(
                        new tools.jackson.databind.ObjectMapper()).readSemanticModel(baseVersion.getSemanticModel());
                return structuralRegenerationService.regenerate(
                        teacherId, baseVersion, plan, baseModel, replacementReferences);
            }
            if (baseVersion.getSemanticModel() == null) {
                throw new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
            }
            if (semanticModificationService == null)
                throw new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
            return semanticModificationService.apply(teacherId, plan.sessionId(), baseVersion, plan.semanticPatch());
        }
        Object result = modificationWorker.execute(teacherId,
                modificationCommand(plan, baseSnapshot, executionBaseVersion,
                        referenceCurriculum, replacementReferences));
        if (plan.action() == EditAction.REPLACE && decisionEventService != null) decisionEventService.recordReplacement(
                teacherId, plan.sessionId(), plan.baseVersionId(), plan.requestId(), plan.instructions());
        return result;
    }

    private CurriculumScope currentCurriculum(
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot) {
        if (curriculumUnitQueryService == null || snapshot.metadata().subUnitId() == null) return null;
        var path = curriculumUnitQueryService.getPathsBySubUnitIds(
                java.util.Set.of(snapshot.metadata().subUnitId())).get(snapshot.metadata().subUnitId());
        if (path == null) return null;
        return new CurriculumScope(path.curriculumRevision(), path.schoolLevel(), path.grade(),
                path.semester() == null ? null : path.semester().intValue(), path.achievementStandardId(),
                path.subUnitId(), path.majorUnitName(), path.middleUnitName(), path.subUnitName());
    }

    /** 문제은행에서 재진입한 최종화 세션은 Version에 sourceQuestionId가 없을 수 있어 최종 문항을 기준으로 추출한다. */
    private com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionResult
    extractFinalizedQuestionSemantic(long teacherId, ProblemEditExecutionPlan plan,
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot) {
        var session = sessionRepository.findByIdAndOwnerTeacherId(plan.sessionId(), teacherId)
                .orElseThrow();
        if (session.getFinalizedQuestionId() == null) {
            return new com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionResult(
                    SemanticExtractionStatus.UNSUPPORTED, null, java.util.List.of("source question이 없습니다."));
        }
        return semanticExtractionService.ensureQuestionSemantic(session.getFinalizedQuestionId(),
                currentCurriculum(snapshot), snapshot);
    }

    /** 수정 기준 Version에 저장된 semantic model을 후보 검증 경로로 전달한다. */
    private com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1 semanticModel(
            ProblemAuthoringVersion version) {
        if (version == null || version.getSemanticModel() == null) return null;
        return jsonCodec.read(version.getSemanticModel(),
                com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1.class);
    }

    /** 수정 기준 Version의 구조화 자산 계획을 보존한 실행 명령을 만든다. */
    private com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand modificationCommand(
            ProblemEditExecutionPlan plan,
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot,
            ProblemAuthoringVersion version,
            CurriculumScope curriculum,
            List<GenerationReference> references) {
        return new com.cenedu.backend.domain.problem.authoring.edit.ProblemModificationCommand(
                plan.requestId(), plan, snapshot, semanticModel(version), assetPlans(version),
                curriculum, references, List.of());
    }

    /** 현재 소단원 정보를 우선하고 없으면 저장된 semantic 교육과정을 사용한다. */
    private CurriculumScope referenceCurriculum(
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot,
            ProblemAuthoringVersion version) {
        CurriculumScope current = currentCurriculum(snapshot);
        if (current != null) return current;
        var model = semanticModel(version);
        return model == null ? null : model.curriculum();
    }

    /** 문제은행 교체 미스 후에만 교사 지시와 현재 문항으로 EXAMPLE을 검색한다. */
    List<GenerationReference> replacementReferences(
            ProblemEditExecutionPlan plan,
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 snapshot,
            ProblemAuthoringVersion version,
            CurriculumScope curriculum) {
        if (plan.action() != EditAction.REPLACE || referenceRetrievalPort == null
                || ragProperties == null || !ragProperties.enabled() || curriculum == null) {
            return List.of();
        }
        Long originQuestionId = version == null ? null : version.getSourceQuestionId();
        if (originQuestionId == null) originQuestionId = snapshot.metadata().derivedFromQuestionId();
        if (originQuestionId == null) return List.of();
        var requested = plan.requestedSpecification();
        var questionType = requested != null && requested.questionType() != null
                ? requested.questionType() : snapshot.metadata().questionType();
        String difficulty = requested != null && requested.difficulty() != null
                ? requested.difficulty() : snapshot.metadata().difficulty();
        int candidateLimit = Math.max(1, Math.min(40, ragProperties.candidateLimit()));
        int selectionLimit = Math.min(4, candidateLimit);
        ProblemReferenceQuery query = ProblemReferenceQuery.withQueryHint(
                java.util.UUID.randomUUID(), GenerationPurpose.PERSONALIZED_APPLICATION,
                curriculum, questionType, difficulty, originQuestionId, snapshot,
                candidateLimit, selectionLimit, usedQuestionIds(plan, snapshot), queryHint(plan));
        try {
            List<com.cenedu.backend.domain.problem.authoring.retrieval.RetrievedProblemReference> retrieved =
                    referenceRetrievalPort.retrieve(query);
            log.info("event=problem_edit_retrieval outcome=SUCCESS requestId={} referenceCount={}",
                    query.retrievalRequestId(), retrieved.size());
            return retrieved.stream().map(reference -> new GenerationReference(
                    GenerationReferenceRole.EXAMPLE, reference.questionId(), reference.snapshot())).toList();
        } catch (RuntimeException exception) {
            log.warn("event=problem_edit_retrieval outcome=FALLBACK requestId={} exceptionType={}",
                    query.retrievalRequestId(), exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private String queryHint(ProblemEditExecutionPlan plan) {
        if (plan.instructions() == null) return null;
        return plan.instructions().stream()
                .map(com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction::instruction)
                .filter(value -> value != null && !value.isBlank())
                .distinct().collect(java.util.stream.Collectors.joining(" | "));
    }

    /** Version 자산 manifest를 읽고 사용할 수 있는 생성 계획만 반환한다. */
    private List<GeneratedAssetPlan> assetPlans(ProblemAuthoringVersion version) {
        if (version == null || version.getAssetManifest() == null
                || version.getAssetManifest().isBlank()) return List.of();
        try {
            DraftAssetManifest manifest = jsonCodec.read(
                    version.getAssetManifest(), DraftAssetManifest.class);
            return manifest == null || manifest.plans() == null ? List.of() : manifest.plans();
        } catch (RuntimeException exception) {
            return List.of();
        }
    }

    private ProblemModificationExecutionResult legacyFallbackResult(ProblemEditExecutionPlan plan, Object result) {
        Long versionId = null;
        boolean promoted = false;
        if (result instanceof com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult processed) {
            versionId = processed.versionId();
            promoted = processed.promoted();
        }
        var mode = plan.semanticPatch() == null
                ? com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.REJECTED
                : plan.semanticPatch().mode();
        return new ProblemModificationExecutionResult(versionId, mode,
                new com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticDiff(
                        java.util.List.of(), java.util.Set.of(), mode == com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.STRUCTURAL_REGENERATION, true),
                promoted, true);
    }

    /**
     * 요청 조건에 맞는 다른 문항을 문제은행에서 찾아 AI 호출 없이 현재 Version으로 교체한다.
     *
     * <p>조건에 맞는 재사용 가능한 문항이 없으면 null을 반환해 호출자가 생성 경로로 넘어가게 한다.
     * 실패를 예외로 올리지 않는 이유는 이 경로가 "먼저 시도해 보는" 최적화이지 요청의 성패를
     * 결정하는 단계가 아니기 때문이다.
     */
    private ProblemModificationExecutionResult tryBankReuse(long teacherId, ProblemEditExecutionPlan plan,
                              com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        var requested = plan.requestedSpecification();
        var type = requested == null || requested.questionType() == null
                ? baseSnapshot.metadata().questionType() : requested.questionType();
        String difficultyValue = requested == null || requested.difficulty() == null
                ? baseSnapshot.metadata().difficulty() : requested.difficulty();
        Short difficulty = difficulty(difficultyValue);
        if (difficulty == null || baseSnapshot.metadata().subUnitId() == null) return null;
        // 후보를 하나만 뽑으면 그 하나가 구조 검증에 걸리거나 자료 조건이 맞지 않는 순간
        // 조건에 맞는 문항이 더 있어도 생성 경로로 새어 나간다.
        var candidates = questionSelector.selectAvailable(baseSnapshot.metadata().subUnitId(),
                difficulty, type, BANK_CANDIDATE_LIMIT, usedQuestionIds(plan, baseSnapshot),
                requested == null ? null : requested.requiresAsset());
        if (candidates.isEmpty()) return null;
        var bank = bankSnapshotQueryService.getSnapshots(candidates.stream()
                        .map(com.cenedu.backend.domain.problem.entity.ProblemQuestion::getId).toList()).stream()
                .filter(com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult::reusable)
                .filter(candidate -> matchesAssetRequirement(requested, candidate))
                .findFirst().orElse(null);
        if (bank == null) return null;
        var session = sessionRepository.findOwnedByIdForUpdate(plan.sessionId(), teacherId)
                .orElseThrow(() -> new com.cenedu.backend.global.common.BusinessException(
                        com.cenedu.backend.global.common.ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
        int versionNo = versionRepository.findFirstBySessionIdOrderByVersionNoDesc(plan.sessionId())
                .map(previous -> previous.getVersionNo() + 1).orElse(1);
        ProblemAuthoringVersion version = versionRepository.save(ProblemAuthoringVersion.create(
                plan.sessionId(), versionNo, plan.baseVersionId(), plan.requestId(),
                AuthoringOperationType.BANK_REUSE, bank.questionId(), 1,
                jsonCodec.write(bank.snapshot()),
                jsonCodec.write(DraftAssetManifest.forBankReuse(bank.assetStorageKeys())),
                "문제은행 교체"));
        version.startVerification(java.util.UUID.nameUUIDFromBytes(
                ("bank-edit:" + plan.requestId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        version.passVerification("{\"source\":\"BANK_REUSE\"}");
        session.attachPendingVersion(version.getId());
        session.promotePendingVersion(version.getId(), version.getVerificationStatus());
        return new ProblemModificationExecutionResult(version.getId(),
                com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticEditMode.BANK_REUSE,
                new com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticDiff(List.of(),
                        java.util.EnumSet.allOf(com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticImpactArea.class),
                        true, false),
                true, false);
    }

    /**
     * 이미 이 Session에서 거쳐 간 문항을 후보에서 제외한다.
     *
     * <p>제외하지 않으면 "다른 문제로 바꿔줘"를 반복할 때 같은 문항이 계속 돌아온다 — 특히
     * 소단원·난이도·유형이 모두 같은 후보 풀이 좁을 때 첫 후보가 원래 문항 자신인 경우가 흔하다.
     *
     * <p>학습지 문항을 다시 수정하는 Session이면 그 학습지에 이미 들어 있는 문항도 함께 뺀다.
     * 조회 단계에서 빼지 않으면 교체를 확정하는 순간 학습지 문항 중복으로 뒤늦게 실패한다.
     */
    private java.util.Set<Long> usedQuestionIds(ProblemEditExecutionPlan plan,
            com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1 baseSnapshot) {
        java.util.Set<Long> used = new java.util.LinkedHashSet<>();
        if (baseSnapshot.metadata().derivedFromQuestionId() != null) {
            used.add(baseSnapshot.metadata().derivedFromQuestionId());
        }
        versionRepository.findAllBySessionIdOrderByVersionNo(plan.sessionId()).stream()
                .map(ProblemAuthoringVersion::getSourceQuestionId)
                .filter(java.util.Objects::nonNull).forEach(used::add);
        if (replacementExclusionPort != null) {
            used.addAll(replacementExclusionPort.excludedQuestionIds(plan.sessionId()));
        }
        return used;
    }

    /** 교사가 자료 유무를 조건으로 걸었을 때만 후보의 자산 보유 여부를 확인한다. */
    private boolean matchesAssetRequirement(
            com.cenedu.backend.domain.problem.authoring.edit.RequestedProblemSpecification requested,
            com.cenedu.backend.domain.problem.authoring.snapshot.BankSnapshotResult candidate) {
        if (requested == null || requested.requiresAsset() == null) return true;
        boolean hasAsset = candidate.snapshot() != null && candidate.snapshot().assets() != null
                && !candidate.snapshot().assets().isEmpty();
        return requested.requiresAsset() == hasAsset;
    }

    /** 스냅샷이 예상 밖의 난이도 표기를 담고 있으면 교체를 포기하고 생성 경로로 넘긴다. */
    private Short difficulty(String value) {
        if (value == null) return null;
        return switch (value) { case "low" -> (short) 1; case "mid" -> (short) 2;
            case "high" -> (short) 3; default -> null; };
    }
}
