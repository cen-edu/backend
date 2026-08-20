package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.authoring.asset.AssetProductionContext;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingRequest;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProcessingResult;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateSourceType;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.port.ProblemAssetProductionPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemVerificationPort;
import com.cenedu.backend.domain.problem.authoring.port.ProblemRepairPort;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairCommand;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairDelta;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairPlan;
import com.cenedu.backend.domain.problem.authoring.candidate.CandidateProvenance;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotNormalizedValidator;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationProfile;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticMaterializationReport;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.DefaultProblemSemanticMaterializer;
import com.cenedu.backend.ai.problem.adapter.semantic.SemanticAuthoringProperties;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecValidator;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringSession;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringSessionRepository;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/** 생성·수정 후보를 S1 검증, Version 보관, 의미·자산 검증, current 승격 순서로 조율한다. */
@Service
public class ProblemCandidateProcessingService {
    private static final Logger log = LoggerFactory.getLogger(ProblemCandidateProcessingService.class);
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final SnapshotStructuralValidator structuralValidator;
    private final SnapshotNormalizedValidator normalizedValidator;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ObjectProvider<ProblemVerificationPort> verificationPortProvider;
    private final ObjectProvider<ProblemAssetProductionPort> assetPortProvider;
    private final ObjectProvider<ProblemRepairPort> repairPortProvider;
    private final ProblemRepairPlanner repairPlanner;
    private final ProblemRepairDeltaMerger repairDeltaMerger;
    private final TransactionTemplate transactionTemplate;
    private final ProblemAiConcurrencyLimiter concurrencyLimiter;
    private final ProblemSemanticMaterializer semanticMaterializer = new DefaultProblemSemanticMaterializer();
    private final ProblemSemanticDocumentCodec semanticDocumentCodec =
            new ProblemSemanticDocumentCodec(new tools.jackson.databind.ObjectMapper());
    private final SemanticAuthoringProperties semanticProperties;

    public ProblemCandidateProcessingService(
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            SnapshotStructuralValidator structuralValidator,
            SnapshotNormalizedValidator normalizedValidator,
            ProblemAuthoringJsonCodec jsonCodec,
            ObjectProvider<ProblemVerificationPort> verificationPortProvider,
            ObjectProvider<ProblemAssetProductionPort> assetPortProvider,
            PlatformTransactionManager transactionManager,
            ProblemAiConcurrencyLimiter concurrencyLimiter
    ) {
        this(sessionRepository, versionRepository, structuralValidator, normalizedValidator, jsonCodec,
                verificationPortProvider, assetPortProvider, transactionManager, concurrencyLimiter,
                new SemanticAuthoringProperties(false), null, new ProblemRepairPlanner(), new ProblemRepairDeltaMerger(new tools.jackson.databind.ObjectMapper()));
    }

    public ProblemCandidateProcessingService(
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            SnapshotStructuralValidator structuralValidator,
            SnapshotNormalizedValidator normalizedValidator,
            ProblemAuthoringJsonCodec jsonCodec,
            ObjectProvider<ProblemVerificationPort> verificationPortProvider,
            ObjectProvider<ProblemAssetProductionPort> assetPortProvider,
            PlatformTransactionManager transactionManager,
            ProblemAiConcurrencyLimiter concurrencyLimiter,
            SemanticAuthoringProperties semanticProperties
    ) {
        this(sessionRepository, versionRepository, structuralValidator, normalizedValidator, jsonCodec,
                verificationPortProvider, assetPortProvider, transactionManager, concurrencyLimiter,
                semanticProperties, null, new ProblemRepairPlanner(), new ProblemRepairDeltaMerger(new tools.jackson.databind.ObjectMapper()));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ProblemCandidateProcessingService(
            ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository,
            SnapshotStructuralValidator structuralValidator,
            SnapshotNormalizedValidator normalizedValidator,
            ProblemAuthoringJsonCodec jsonCodec,
            ObjectProvider<ProblemVerificationPort> verificationPortProvider,
            ObjectProvider<ProblemAssetProductionPort> assetPortProvider,
            PlatformTransactionManager transactionManager,
            ProblemAiConcurrencyLimiter concurrencyLimiter,
            SemanticAuthoringProperties semanticProperties,
            ObjectProvider<ProblemRepairPort> repairPortProvider,
            ProblemRepairPlanner repairPlanner,
            ProblemRepairDeltaMerger repairDeltaMerger
    ) {
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.structuralValidator = structuralValidator;
        this.normalizedValidator = normalizedValidator;
        this.jsonCodec = jsonCodec;
        this.verificationPortProvider = verificationPortProvider;
        this.assetPortProvider = assetPortProvider;
        this.repairPortProvider = repairPortProvider;
        this.repairPlanner = repairPlanner;
        this.repairDeltaMerger = repairDeltaMerger;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.concurrencyLimiter = concurrencyLimiter;
        this.semanticProperties = semanticProperties;
    }

    /** 외부 AI 호출을 트랜잭션 밖에서 수행하고 최종 PASSED 후보만 current로 승격한다. */
    public CandidateProcessingResult process(CandidateProcessingRequest request) {
        return processInternal(request, true, VerificationProfile.FULL_CONTENT);
    }

    private CandidateProcessingResult processInternal(CandidateProcessingRequest request, boolean allowRepair,
                                                      VerificationProfile profile) {
        validateRequest(request);
        long startedAt = System.nanoTime();
        log.info("event=problem_authoring_stage operation={} stage=REGISTRATION outcome=STARTED "
                        + "jobId={} itemId={} sessionId={} operationId={}",
                request.operationType(), context("jobId"), context("itemId"), context("sessionId"), context("operationId"));
        RegisteredCandidate registered = Objects.requireNonNull(
                transactionTemplate.execute(status -> registerCandidate(request)));
        log.info("event=problem_authoring_stage operation={} stage=REGISTRATION outcome=SUCCESS elapsedMs={} versionId={} "
                        + "jobId={} itemId={} sessionId={} operationId={}",
                request.operationType(), elapsedMs(startedAt), registered.versionId(),
                context("jobId"), context("itemId"), context("sessionId"), context("operationId"));

        long assetStartedAt = System.nanoTime();
        DraftAssetManifest manifest = produceAssets(request, registered);
        log.info("event=problem_authoring_stage operation={} stage=ASSET outcome={} elapsedMs={} assetCount={} "
                        + "jobId={} itemId={} sessionId={} operationId={}",
                request.operationType(), assetOutcome(manifest), elapsedMs(assetStartedAt), manifest.plans().size(),
                context("jobId"), context("itemId"), context("sessionId"), context("operationId"));
        UUID verificationRequestId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> beginVerification(
                registered, manifest, verificationRequestId));

        long verificationStartedAt = System.nanoTime();
        ProblemVerificationBundle bundle = verify(request, manifest, verificationRequestId, profile);
        log.info("event=problem_authoring_stage operation={} stage=VERIFICATION outcome={} elapsedMs={} verificationRequestId={} "
                        + "jobId={} itemId={} sessionId={} operationId={}",
                request.operationType(), bundle.overallStatus(), elapsedMs(verificationStartedAt), verificationRequestId,
                context("jobId"), context("itemId"), context("sessionId"), context("operationId"));
        ProblemVerificationBundle completedBundle = bundle;
        long promotionStartedAt = System.nanoTime();
        transactionTemplate.executeWithoutResult(status -> completeVerification(
                request, registered, completedBundle));
        log.info("event=problem_authoring_stage operation={} stage=PROMOTION outcome={} elapsedMs={} versionId={} "
                        + "jobId={} itemId={} sessionId={} operationId={}",
                request.operationType(), bundle.overallStatus(), elapsedMs(promotionStartedAt), registered.versionId(),
                context("jobId"), context("itemId"), context("sessionId"), context("operationId"));

        CandidateProcessingResult result = new CandidateProcessingResult(
                registered.versionId(),
                registered.versionNo(),
                verificationRequestId,
                bundle.overallStatus(),
                bundle,
                bundle.overallStatus() == VerificationOverallStatus.PASSED);
        if (allowRepair && result.status() == VerificationOverallStatus.FAILED) {
            CandidateProcessingResult repaired = repairFailedCandidate(request, registered, bundle);
            if (repaired != null) return repaired;
        }
        return result;
    }

    /** 실패 원인이 부분 수정 가능할 때만 새 Version을 만들고 생성 Port 없이 재검증한다. */
    private CandidateProcessingResult repairFailedCandidate(
            CandidateProcessingRequest request,
            RegisteredCandidate registered,
            ProblemVerificationBundle bundle
    ) {
        if (request.candidate().semanticModel() != null || repairPortProvider == null) return null;
        ProblemRepairPlan plan = repairPlanner.plan(bundle);
        if (!plan.repairable()) return null;
        ProblemRepairPort repairPort = repairPortProvider.getIfAvailable();
        if (repairPort == null) return null;
        ProblemRepairDelta delta = repairPort.repair(new ProblemRepairCommand(
                UUID.randomUUID(), request.candidate().snapshot(), plan));
        var repairedSnapshot = repairDeltaMerger.merge(request.candidate().snapshot(), plan, delta);
        structuralValidator.validate(repairedSnapshot);
        normalizedValidator.validate(repairedSnapshot);
        ProblemCandidateDraft repairedCandidate = ProblemCandidateDraft.legacy(
                UUID.randomUUID(), repairedSnapshot, request.candidate().assetPlans(),
                new CandidateProvenance(CandidateSourceType.AI_MODIFY,
                        registered.versionId(), List.of(registered.versionId())));
        CandidateProcessingRequest repairedRequest = new CandidateProcessingRequest(
                request.ownerTeacherId(), request.sessionId(), registered.versionId(),
                AuthoringOperationType.AI_MODIFY, request.verificationOperationType(), repairedCandidate,
                request.expectation(), request.verificationContext(), "검증 오류 항목 부분 수정");
        return processInternal(repairedRequest, false, repairProfile(plan));
    }

    private VerificationProfile repairProfile(ProblemRepairPlan plan) {
        Set<com.cenedu.backend.domain.problem.authoring.repair.RepairTarget> targets = plan.targets();
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.ANSWERS)
                || targets.contains(com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.CHOICES)
                || targets.contains(com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.STEPS)) {
            return VerificationProfile.ANSWER_RELATED;
        }
        if (targets.contains(com.cenedu.backend.domain.problem.authoring.repair.RepairTarget.RUBRIC)) {
            return VerificationProfile.RUBRIC_ONLY;
        }
        return VerificationProfile.ORIGINAL_ONLY;
    }

    /** 자산 계획의 처리 결과를 본문 없이 요약해 로그에 남긴다. */
    private String assetOutcome(DraftAssetManifest manifest) {
        if (manifest.plans().isEmpty()) return "NOT_APPLICABLE";
        return manifest.artifacts().stream().allMatch(artifact ->
                artifact.status() == com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus.READY)
                ? "SUCCESS" : "INCOMPLETE";
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }

    /** MDC 추적값이 없는 직접 호출 테스트에서도 공통 로그 형식을 유지한다. */
    private String context(String key) {
        String value = MDC.get(key);
        return value == null ? "" : value;
    }

    private RegisteredCandidate registerCandidate(CandidateProcessingRequest request) {
        ProblemAuthoringSession session = getOwnedSessionForUpdate(
                request.sessionId(), request.ownerTeacherId());
        validateParent(request, session);

        int versionNo = versionRepository
                .findFirstBySessionIdOrderByVersionNoDesc(request.sessionId())
                .map(previous -> previous.getVersionNo() + 1)
                .orElse(1);
        DraftAssetManifest manifest = DraftAssetManifest.planned(
                request.candidate().assetPlans());
        SemanticModelDocument semanticDocument = request.candidate().semanticModel() == null
                ? null : semanticDocumentCodec.semanticModel(request.candidate().semanticModel());
        ProblemAuthoringVersion version = ProblemAuthoringVersion.create(
                request.sessionId(),
                versionNo,
                request.parentVersionId(),
                request.candidate().requestId(),
                request.operationType(),
                request.candidate().provenance().sourceQuestionId(),
                request.candidate().snapshot().schemaVersion(),
                jsonCodec.write(request.candidate().snapshot()), semanticDocument,
                jsonCodec.write(manifest), request.changeSummary());
        versionRepository.saveAndFlush(version);
        session.attachPendingVersion(version.getId());
        return new RegisteredCandidate(version.getId(), versionNo);
    }

    private DraftAssetManifest produceAssets(CandidateProcessingRequest request,
                                             RegisteredCandidate registered) {
        ProblemCandidateDraft candidate = request.candidate();
        List<GeneratedAssetPlan> plans = candidate.assetPlans() == null
                ? List.of()
                : List.copyOf(candidate.assetPlans());
        if (plans.isEmpty()) {
            return DraftAssetManifest.planned(plans);
        }

        ProblemAssetProductionPort assetPort = assetPortProvider.getIfAvailable();
        if (assetPort == null) {
            return failedAssetManifest(plans, "ASSET_PORT_NOT_CONFIGURED");
        }

        AssetProductionContext context = new AssetProductionContext(
                request.sessionId(),
                registered.versionNo(),
                candidate.snapshot().metadata().questionType());
        List<DraftAssetArtifact> artifacts = new ArrayList<>();
        for (GeneratedAssetPlan plan : plans) {
            try {
                DraftAssetArtifact artifact;
                try (ProblemAiConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
                    artifact = assetPort.produce(plan, context);
                }
                if (artifact == null || !plan.assetKey().equals(artifact.assetKey())) {
                    artifacts.add(failedArtifact(
                            plan.assetKey(), "INVALID_ASSET_ARTIFACT"));
                } else {
                    artifacts.add(artifact);
                }
            } catch (RuntimeException exception) {
                artifacts.add(failedArtifact(plan.assetKey(), "ASSET_PRODUCTION_ERROR"));
            }
        }
        return new DraftAssetManifest(
                DraftAssetManifest.CURRENT_SCHEMA_VERSION, plans, List.copyOf(artifacts));
    }

    private void beginVerification(RegisteredCandidate registered,
                                   DraftAssetManifest manifest,
                                   UUID verificationRequestId) {
        ProblemAuthoringVersion version = versionRepository
                .findById(registered.versionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        version.updateAssetManifest(jsonCodec.write(manifest));
        version.startVerification(verificationRequestId);
    }

    private ProblemVerificationBundle verify(CandidateProcessingRequest request,
                                             DraftAssetManifest manifest,
                                             UUID verificationRequestId,
                                             VerificationProfile profile) {
        ProblemVerificationPort verificationPort = verificationPortProvider.getIfAvailable();
        if (verificationPort == null) {
            return ProblemVerificationBundle.contentOnly(
                    verificationRequestId,
                    errorReport(verificationRequestId, VerificationScope.CONTENT,
                            "VERIFICATION_PORT_NOT_CONFIGURED"));
        }

        ProblemVerificationReport content = callVerification(
                verificationPort,
                verificationRequest(request, manifest, verificationRequestId,
                        VerificationScope.CONTENT, profile));
        if (manifest.plans().isEmpty()) {
            return ProblemVerificationBundle.contentOnly(verificationRequestId, content);
        }
        ProblemVerificationReport asset = manifest.artifacts().stream()
                .anyMatch(artifact -> artifact.status()
                        != com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus.READY)
                ? errorReport(verificationRequestId, VerificationScope.ASSET,
                        "ASSET_PRODUCTION_INCOMPLETE")
                : callVerification(
                        verificationPort,
                        verificationRequest(request, manifest, verificationRequestId,
                                VerificationScope.ASSET, profile));
        return ProblemVerificationBundle.merge(verificationRequestId, content, asset);
    }

    private ProblemVerificationRequest verificationRequest(
            CandidateProcessingRequest request,
            DraftAssetManifest manifest,
            UUID verificationRequestId,
            VerificationScope scope,
            VerificationProfile profile
    ) {
        return new ProblemVerificationRequest(
                verificationRequestId,
                scope,
                request.verificationOperationType(),
                request.candidate(),
                manifest,
                request.expectation(),
                request.verificationContext(), semanticReport(request.candidate()), profile);
    }

    private ProblemVerificationReport callVerification(
            ProblemVerificationPort port,
            ProblemVerificationRequest request
    ) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                ProblemVerificationReport report;
                try (ProblemAiConcurrencyLimiter.Permit ignored = concurrencyLimiter.acquire()) {
                    report = port.verify(request);
                }
                if (report == null
                        || !request.verificationRequestId().equals(report.requestId())
                        || report.scope() != request.scope()) {
                    return errorReport(request.verificationRequestId(), request.scope(),
                            "INVALID_VERIFICATION_REPORT");
                }
                if (attempt == 1 && retryableVerificationError(report)) {
                    log.warn("검증 일시 오류 — 검증만 재시도합니다. requestId={}, scope={}, attempt=2",
                            request.verificationRequestId(), request.scope());
                    continue;
                }
                return report;
            } catch (RuntimeException exception) {
                if (attempt == 1 && retryableVerificationException(exception)) {
                    log.warn("검증 공급자 예외 — 검증만 재시도합니다. requestId={}, scope={}, attempt=2",
                            request.verificationRequestId(), request.scope());
                    continue;
                }
                return errorReport(request.verificationRequestId(), request.scope(),
                        "VERIFICATION_PROVIDER_ERROR");
            }
        }
        return errorReport(request.verificationRequestId(), request.scope(),
                "VERIFICATION_PROVIDER_ERROR");
    }

    /** 내용 실패는 재시도하지 않고 형식·공급자 오류만 한 번 재검증한다. */
    private boolean retryableVerificationError(ProblemVerificationReport report) {
        if (report.overallStatus() != VerificationOverallStatus.ERROR
                || report.findings() == null || report.findings().isEmpty()) {
            return false;
        }
        return report.findings().stream().allMatch(finding ->
                finding.status() == VerificationFindingStatus.ERROR
                        && (finding.code() == VerificationIssueCode.PROVIDER_ERROR
                        || (finding.message() != null
                        && finding.message().contains("응답이 요구한 형식"))));
    }

    private boolean retryableVerificationException(RuntimeException exception) {
        if (exception instanceof BusinessException businessException) {
            return businessException.getErrorCode() == ErrorCode.AI_CLIENT_CALL_FAILED;
        }
        return exception instanceof com.cenedu.backend.ai.verification.adapter.SolverResponseParseException;
    }

    private void completeVerification(CandidateProcessingRequest request,
                                      RegisteredCandidate registered,
                                      ProblemVerificationBundle bundle) {
        ProblemAuthoringSession session = getOwnedSessionForUpdate(
                request.sessionId(), request.ownerTeacherId());
        ProblemAuthoringVersion version = versionRepository
                .findByIdAndSessionId(registered.versionId(), request.sessionId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        String reportJson = jsonCodec.write(bundle);

        switch (bundle.overallStatus()) {
            case PASSED -> {
                version.passVerification(reportJson);
                session.promotePendingVersion(
                        version.getId(), version.getVerificationStatus());
            }
            case FAILED -> {
                version.failVerification(reportJson);
                session.failPendingVersion(version.getId(), "VERIFICATION_FAILED");
            }
            case ERROR -> {
                version.errorVerification(reportJson);
                session.failPendingVersion(version.getId(), "VERIFICATION_ERROR");
            }
        }
    }

    private void validateRequest(CandidateProcessingRequest request) {
        if (request == null || request.candidate() == null
                || request.candidate().requestId() == null
                || request.candidate().provenance() == null
                || request.operationType() == null
                || request.verificationOperationType() == null
                || request.expectation() == null
                || request.verificationContext() == null) {
            throw new IllegalArgumentException("후보 처리 필수값이 누락되었습니다.");
        }
        validateSemanticCandidate(request.candidate());
        structuralValidator.validate(request.candidate().snapshot());
        normalizedValidator.validate(request.candidate().snapshot());
        validateSourceType(request.operationType(),
                request.candidate().provenance().sourceType());
        validateAssetPlans(request.candidate());
    }

    /** 의미 후보를 다시 계산해 Snapshot·자산 계획이 서버 결과와 일치하는지 확인한다. */
    private void validateSemanticCandidate(ProblemCandidateDraft candidate) {
        if (candidate.semanticModel() == null) {
            if (semanticProperties.enabled()
                    && (candidate.provenance().sourceType() == CandidateSourceType.AI_GENERATE
                    || candidate.provenance().sourceType() == CandidateSourceType.AI_MODIFY)) {
                throw new IllegalArgumentException("semantic authoring 활성화 상태에서는 semantic model이 필요합니다.");
            }
            return;
        }
        MaterializedProblem materialized = semanticMaterializer.materialize(candidate.semanticModel());
        if (!Objects.equals(materialized.snapshot(), candidate.snapshot())
                || !Objects.equals(materialized.assetPlans(), candidate.assetPlans())) {
            throw new IllegalArgumentException("의미 모델과 materialized 후보가 일치하지 않습니다.");
        }
        List<com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1> specs = candidate.assetPlans().stream()
                .filter(plan -> plan.specification() != null && plan.specification().diagramSpec() != null)
                .map(plan -> plan.specification().diagramSpec())
                .toList();
        new DiagramSpecValidator().validateAll(specs, Map.of());
    }

    private SemanticMaterializationReport semanticReport(ProblemCandidateDraft candidate) {
        return candidate.semanticModel() == null ? null
                : semanticMaterializer.materialize(candidate.semanticModel()).report();
    }

    private void validateParent(CandidateProcessingRequest request,
                                ProblemAuthoringSession session) {
        if (request.parentVersionId() != null) {
            versionRepository.findByIdAndSessionId(
                            request.parentVersionId(), request.sessionId())
                    .orElseThrow(() -> new BusinessException(
                            ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        }
        if (request.operationType() == AuthoringOperationType.AI_MODIFY
                && (request.parentVersionId() == null
                || !request.parentVersionId().equals(session.getCurrentVersionId()))) {
            throw new IllegalStateException("수정 후보는 현재 Version을 부모로 사용해야 합니다.");
        }
    }

    private void validateSourceType(AuthoringOperationType operationType,
                                    CandidateSourceType sourceType) {
        if (sourceType == null || !operationType.name().equals(sourceType.name())) {
            throw new IllegalArgumentException("후보 출처와 Version 작업 유형이 일치하지 않습니다.");
        }
    }

    private void validateAssetPlans(ProblemCandidateDraft candidate) {
        Set<String> snapshotKeys = candidate.snapshot().assets().stream()
                .map(asset -> asset.assetKey())
                .collect(Collectors.toSet());
        Set<String> planKeys = (candidate.assetPlans() == null
                ? List.<GeneratedAssetPlan>of()
                : candidate.assetPlans()).stream()
                .map(GeneratedAssetPlan::assetKey)
                .collect(Collectors.toSet());
        if (!snapshotKeys.equals(planKeys)) {
            throw new IllegalArgumentException(
                    "S1 assetKey와 GeneratedAssetPlan assetKey가 일치해야 합니다.");
        }
    }

    private ProblemAuthoringSession getOwnedSessionForUpdate(long sessionId,
                                                              long ownerTeacherId) {
        return sessionRepository.findOwnedByIdForUpdate(sessionId, ownerTeacherId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND));
    }

    private ProblemVerificationReport errorReport(UUID requestId,
                                                  VerificationScope scope,
                                                  String message) {
        VerificationFinding finding = new VerificationFinding(
                scope == VerificationScope.ASSET
                        ? VerificationCheckType.ASSET_CONSISTENCY
                        : VerificationCheckType.CORRECTNESS,
                VerificationFindingStatus.ERROR,
                VerificationSeverity.ERROR,
                VerificationIssueCode.PROVIDER_ERROR,
                message,
                null);
        return new ProblemVerificationReport(
                requestId, scope, VerificationOverallStatus.ERROR, List.of(finding));
    }

    private DraftAssetManifest failedAssetManifest(List<GeneratedAssetPlan> plans,
                                                   String errorCode) {
        List<DraftAssetArtifact> artifacts = plans.stream()
                .map(plan -> failedArtifact(plan.assetKey(), errorCode))
                .toList();
        return new DraftAssetManifest(
                DraftAssetManifest.CURRENT_SCHEMA_VERSION, plans, artifacts);
    }

    private DraftAssetArtifact failedArtifact(String assetKey, String errorCode) {
        return new DraftAssetArtifact(
                assetKey, com.cenedu.backend.domain.problem.authoring.asset.DraftAssetStatus.FAILED,
                null, null, null, null, null, 1, errorCode);
    }

    private record RegisteredCandidate(Long versionId, int versionNo) {
    }
}
