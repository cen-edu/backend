package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.List;
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

/** 생성·수정 후보를 S1 검증, Version 보관, 의미·자산 검증, current 승격 순서로 조율한다. */
@Service
public class ProblemCandidateProcessingService {
    private static final int MAX_VERIFICATION_ERROR_RETRIES = 2;

    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final SnapshotStructuralValidator structuralValidator;
    private final SnapshotNormalizedValidator normalizedValidator;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ObjectProvider<ProblemVerificationPort> verificationPortProvider;
    private final ObjectProvider<ProblemAssetProductionPort> assetPortProvider;
    private final TransactionTemplate transactionTemplate;
    private final ProblemAiConcurrencyLimiter concurrencyLimiter;

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
        this.sessionRepository = sessionRepository;
        this.versionRepository = versionRepository;
        this.structuralValidator = structuralValidator;
        this.normalizedValidator = normalizedValidator;
        this.jsonCodec = jsonCodec;
        this.verificationPortProvider = verificationPortProvider;
        this.assetPortProvider = assetPortProvider;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.concurrencyLimiter = concurrencyLimiter;
    }

    /** 외부 AI 호출을 트랜잭션 밖에서 수행하고 최종 PASSED 후보만 current로 승격한다. */
    public CandidateProcessingResult process(CandidateProcessingRequest request) {
        validateRequest(request);
        RegisteredCandidate registered = Objects.requireNonNull(
                transactionTemplate.execute(status -> registerCandidate(request)));

        DraftAssetManifest manifest = produceAssets(request, registered);
        UUID verificationRequestId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(status -> beginVerification(
                registered, manifest, verificationRequestId));

        ProblemVerificationBundle bundle = verify(request, manifest, verificationRequestId);
        int errorRetries = 0;
        while (bundle.overallStatus() == VerificationOverallStatus.ERROR
                && errorRetries < MAX_VERIFICATION_ERROR_RETRIES) {
            errorRetries++;
            bundle = verify(request, manifest, verificationRequestId);
        }
        ProblemVerificationBundle completedBundle = bundle;
        transactionTemplate.executeWithoutResult(status -> completeVerification(
                request, registered, completedBundle));

        return new CandidateProcessingResult(
                registered.versionId(),
                registered.versionNo(),
                verificationRequestId,
                bundle.overallStatus(),
                bundle,
                bundle.overallStatus() == VerificationOverallStatus.PASSED);
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
        ProblemAuthoringVersion version = ProblemAuthoringVersion.create(
                request.sessionId(),
                versionNo,
                request.parentVersionId(),
                request.candidate().requestId(),
                request.operationType(),
                request.candidate().provenance().sourceQuestionId(),
                request.candidate().snapshot().schemaVersion(),
                jsonCodec.write(request.candidate().snapshot()),
                jsonCodec.write(manifest),
                request.changeSummary());
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
                                             UUID verificationRequestId) {
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
                        VerificationScope.CONTENT));
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
                                VerificationScope.ASSET));
        return ProblemVerificationBundle.merge(verificationRequestId, content, asset);
    }

    private ProblemVerificationRequest verificationRequest(
            CandidateProcessingRequest request,
            DraftAssetManifest manifest,
            UUID verificationRequestId,
            VerificationScope scope
    ) {
        return new ProblemVerificationRequest(
                verificationRequestId,
                scope,
                request.verificationOperationType(),
                request.candidate(),
                manifest,
                request.expectation(),
                request.verificationContext());
    }

    private ProblemVerificationReport callVerification(
            ProblemVerificationPort port,
            ProblemVerificationRequest request
    ) {
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
            return report;
        } catch (RuntimeException exception) {
            return errorReport(request.verificationRequestId(), request.scope(),
                    "VERIFICATION_PROVIDER_ERROR");
        }
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
        structuralValidator.validate(request.candidate().snapshot());
        normalizedValidator.validate(request.candidate().snapshot());
        validateSourceType(request.operationType(),
                request.candidate().provenance().sourceType());
        validateAssetPlans(request.candidate());
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
