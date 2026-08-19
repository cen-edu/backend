package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetStorageKeyFactory;
import com.cenedu.backend.domain.problem.dto.response.FinalizedProblemReferenceResponse;
import com.cenedu.backend.domain.problem.dto.response.ProblemDeploymentStatus;
import com.cenedu.backend.domain.problem.entity.*;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.SemanticModelDocument;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.RenderSpecDocument;

/** PASSED 현재 Version을 문제은행에 원자적으로 최종 저장한다. */
@Service
public class ProblemAuthoringFinalizationService {
    private final ProblemAuthoringSessionRepository sessionRepository;
    private final ProblemAuthoringVersionRepository versionRepository;
    private final ProblemQuestionRepository questionRepository;
    private final ProblemChoiceRepository choiceRepository;
    private final ProblemStepRepository stepRepository;
    private final ProblemAnswerUnitRepository answerUnitRepository;
    private final ProblemRubricItemRepository rubricRepository;
    private final ProblemAssetRepository assetRepository;
    private final ProblemAssetStorageTaskRepository storageTaskRepository;
    private final ProblemSnapshotEntityMapper mapper;
    private final ObjectMapper objectMapper;
    private ProblemSearchIndexingService searchIndexingService;
    private ProblemTeacherDecisionEventService decisionEventService;
    private final ProblemSemanticDocumentCodec semanticDocumentCodec;

    public ProblemAuthoringFinalizationService(ProblemAuthoringSessionRepository sessionRepository,
            ProblemAuthoringVersionRepository versionRepository, ProblemQuestionRepository questionRepository,
            ProblemChoiceRepository choiceRepository, ProblemStepRepository stepRepository,
            ProblemAnswerUnitRepository answerUnitRepository, ProblemRubricItemRepository rubricRepository,
            ProblemAssetRepository assetRepository, ProblemAssetStorageTaskRepository storageTaskRepository,
            ProblemSnapshotEntityMapper mapper, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository; this.versionRepository = versionRepository;
        this.questionRepository = questionRepository; this.choiceRepository = choiceRepository;
        this.stepRepository = stepRepository; this.answerUnitRepository = answerUnitRepository;
        this.rubricRepository = rubricRepository; this.assetRepository = assetRepository;
        this.storageTaskRepository = storageTaskRepository;
        this.mapper = mapper; this.objectMapper = objectMapper;
        this.semanticDocumentCodec = new ProblemSemanticDocumentCodec(objectMapper);
    }

    /** 최종화 이후 검색 인덱싱 큐를 선택적으로 연결한다. */
    @Autowired(required = false)
    void setSearchIndexingService(ProblemSearchIndexingService service) { this.searchIndexingService = service; }

    /** 문제 승인 결정 이벤트 기록기를 선택적으로 연결한다. */
    @Autowired(required = false)
    void setDecisionEventService(ProblemTeacherDecisionEventService service) { this.decisionEventService = service; }

    /** 소유한 Session들을 검증한 뒤 최종 문제 참조를 반환한다. */
    @Transactional
    public List<FinalizedProblemReferenceResponse> finalizeForWorksheet(long ownerTeacherId, List<Long> sessionIds) {
        if (sessionIds == null || sessionIds.isEmpty()) return List.of();
        List<ProblemAuthoringSession> sessions = sessionRepository.findAllForFinalization(sessionIds);
        if (sessions.size() != sessionIds.stream().distinct().count()
                || sessions.stream().anyMatch(s -> !Long.valueOf(ownerTeacherId).equals(s.getOwnerTeacherId()))) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_SESSION_NOT_FOUND);
        }
        // 저장 전에 전체 Session의 상태를 먼저 확인해 부분 최종화를 막는다.
        sessions.forEach(this::validateReadyForFinalization);
        return sessions.stream().map(this::finalizeOne).toList();
    }

    /** 하나라도 실행 중이거나 검증 전이면 전체 최종화를 중단한다. */
    private void validateReadyForFinalization(ProblemAuthoringSession session) {
        if (session.getLifecycleStatus() == AuthoringLifecycleStatus.FINALIZED) return;
        if (session.getCurrentVersionId() == null || session.getPendingVersionId() != null
                || session.getOperationStatus() != AuthoringOperationStatus.IDLE) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        ProblemAuthoringVersion version = versionRepository.findByIdAndSessionId(
                session.getCurrentVersionId(), session.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        if (version.getVerificationStatus() != AuthoringVerificationStatus.PASSED) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
    }

    private FinalizedProblemReferenceResponse finalizeOne(ProblemAuthoringSession session) {
        if (session.getLifecycleStatus() == AuthoringLifecycleStatus.FINALIZED) {
            ProblemAuthoringVersion version = versionRepository.findById(session.getCurrentVersionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
            return new FinalizedProblemReferenceResponse(session.getId(), version.getId(),
                    session.getFinalizedQuestionId(), questionType(version),
                    resolveDeploymentStatus(session.getFinalizedQuestionId()));
        }
        if (session.getCurrentVersionId() == null || session.getPendingVersionId() != null
                || session.getOperationStatus() != AuthoringOperationStatus.IDLE) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        ProblemAuthoringVersion version = versionRepository.findByIdAndSessionId(session.getCurrentVersionId(), session.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_FOUND));
        if (version.getVerificationStatus() != AuthoringVerificationStatus.PASSED) {
            throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_VERSION_NOT_VERIFIED);
        }
        Long questionId;
        if (version.getOperationType() == AuthoringOperationType.BANK_REUSE) {
            questionId = version.getSourceQuestionId();
        } else {
            QuestionSnapshotV1 snapshot = read(version.getSnapshot(), QuestionSnapshotV1.class);
            DraftAssetManifest draftManifest = read(version.getAssetManifest(), DraftAssetManifest.class);
            Map<String, String> keys = draftManifest.artifacts().stream()
                    .collect(java.util.stream.Collectors.toMap(DraftAssetArtifact::assetKey,
                            DraftAssetArtifact::draftStorageKey));
            ProblemQuestion derivedFrom = snapshot.metadata().derivedFromQuestionId() == null ? null
                    : questionRepository.findById(snapshot.metadata().derivedFromQuestionId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID));
            SemanticModelDocument semanticModel = version.getSemanticModel() == null ? null
                    : semanticDocumentCodec.semanticModel(
                            semanticDocumentCodec.readSemanticModel(version.getSemanticModel()));
            Map<String, RenderSpecDocument> renderSpecs = draftManifest.plans().stream()
                    .filter(plan -> plan.specification() != null && plan.specification().diagramSpec() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            GeneratedAssetPlan::assetKey,
                            plan -> semanticDocumentCodec.renderSpec(
                                    plan.specification().diagramSpec(), "semantic-svg-v1")));
            ProblemQuestionPersistenceBundle bundle = mapper.map(snapshot, keys, derivedFrom,
                    semanticModel, renderSpecs);
            ProblemQuestion question = questionRepository.save(bundle.question());
            choiceRepository.saveAll(bundle.choices()); stepRepository.saveAll(bundle.steps());
            answerUnitRepository.saveAll(bundle.answerUnits()); rubricRepository.saveAll(bundle.rubricItems());
            DraftAssetManifest manifest = draftManifest;
            Map<String, DraftAssetArtifact> artifacts = manifest.artifacts().stream()
                    .collect(java.util.stream.Collectors.toMap(DraftAssetArtifact::assetKey, a -> a));
            Map<String, GeneratedAssetPlan> plans = manifest.plans().stream()
                    .collect(java.util.stream.Collectors.toMap(GeneratedAssetPlan::assetKey, p -> p));
            for (ProblemAsset asset : bundle.assets()) {
                DraftAssetArtifact artifact = artifacts.get(asset.getAssetKey());
                GeneratedAssetPlan plan = plans.get(asset.getAssetKey());
                if (artifact == null || plan == null || artifact.checksum() == null) {
                    throw new BusinessException(ErrorCode.PROBLEM_ASSET_NOT_READY);
                }
                String finalKey = GeneratedAssetStorageKeyFactory.finalKey(question.getId(),
                        snapshot.metadata().questionType(), asset.getAssetKey(), artifact.checksum(), plan.outputFormat());
                asset.replaceImage(finalKey, nonNull(artifact.widthPx()), nonNull(artifact.heightPx()));
                asset.markPending();
                assetRepository.save(asset);
                storageTaskRepository.save(ProblemAssetStorageTask.create(asset, artifact.draftStorageKey(), finalKey,
                        artifact.checksum(), artifact.contentType()));
            }
            questionId = question.getId();
        }
        if (questionId == null) throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        session.finalizeAs(questionId, version.getVerificationStatus());
        if (decisionEventService != null) {
            decisionEventService.recordApproval(session.getOwnerTeacherId(), session.getId(), version.getId());
        }
        if (searchIndexingService != null) {
            searchIndexingService.enqueueFinalized(questionId, version.getId(),
                    read(version.getSnapshot(), QuestionSnapshotV1.class));
        }
        return new FinalizedProblemReferenceResponse(session.getId(), version.getId(), questionId,
                questionType(version), resolveDeploymentStatus(questionId));
    }

    /** 최종 문항 자산 전체를 기준으로 Worksheet 공개 배포 상태를 계산한다. */
    ProblemDeploymentStatus resolveDeploymentStatus(Long questionId) {
        List<ProblemAssetStorageStatus> statuses = assetRepository.findStorageStatusesByQuestionId(questionId);
        if (statuses.stream().anyMatch(status -> status == ProblemAssetStorageStatus.FAILED)) {
            return ProblemDeploymentStatus.BLOCKED_BY_ASSET_FAILURE;
        }
        if (statuses.stream().allMatch(status -> status == ProblemAssetStorageStatus.READY)) {
            return ProblemDeploymentStatus.READY;
        }
        return ProblemDeploymentStatus.WAITING_FOR_ASSETS;
    }

    private QuestionType questionType(ProblemAuthoringVersion version) {
        return read(version.getSnapshot(), QuestionSnapshotV1.class).metadata().questionType();
    }
    private int nonNull(Integer value) { return value == null ? 0 : value; }
    private <T> T read(String json, Class<T> type) {
        try { return objectMapper.readValue(json, type); }
        catch (Exception e) { throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID); }
    }
}
