package com.cenedu.backend.domain.problem.service;

import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetArtifact;
import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetStorageKeyFactory;
import com.cenedu.backend.domain.problem.dto.response.FinalizedProblemReferenceResponse;
import com.cenedu.backend.domain.problem.entity.*;
import com.cenedu.backend.domain.problem.entity.enums.*;
import com.cenedu.backend.domain.problem.repository.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.QuestionType;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

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
    }

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
                    session.getFinalizedQuestionId(), questionType(version));
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
            ProblemQuestionPersistenceBundle bundle = mapper.map(snapshot, keys);
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
                storageTaskRepository.save(ProblemAssetStorageTask.create(asset, artifact.draftStorageKey(), finalKey));
            }
            questionId = question.getId();
        }
        if (questionId == null) throw new BusinessException(ErrorCode.PROBLEM_AUTHORING_DATA_INVALID);
        session.finalizeAs(questionId, version.getVerificationStatus());
        return new FinalizedProblemReferenceResponse(session.getId(), version.getId(), questionId, questionType(version));
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
