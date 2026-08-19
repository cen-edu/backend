package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.verification.*;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.domain.problem.repository.ProblemAuthoringVersionRepository;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 구조적 semantic 요청을 정제된 generation port로 재생성한다. */
@Service
public class ProblemStructuralRegenerationService {
    private final ObjectProvider<ProblemGenerationPort> generationPortProvider;
    private final ProblemCandidateProcessingService processingService;
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemAuthoringVersionRepository versionRepository;

    public ProblemStructuralRegenerationService(ObjectProvider<ProblemGenerationPort> generationPortProvider,
            ProblemCandidateProcessingService processingService, ProblemAuthoringJsonCodec jsonCodec,
            ProblemAuthoringVersionRepository versionRepository) {
        this.generationPortProvider = generationPortProvider;
        this.processingService = processingService;
        this.jsonCodec = jsonCodec;
        this.versionRepository = versionRepository;
    }

    /** 현재 문항을 ORIGIN으로만 전달해 구조 변경 후보를 생성·검증한다. */
    public ProblemModificationExecutionResult regenerate(long ownerTeacherId,
            ProblemAuthoringVersion baseVersion, ProblemEditExecutionPlan plan,
            ProblemSemanticModelV1 baseModel) {
        ProblemGenerationPort port = generationPortProvider.getIfAvailable();
        if (port == null) throw new BusinessException(ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
        QuestionSnapshotV1 baseSnapshot = jsonCodec.read(baseVersion.getSnapshot(), QuestionSnapshotV1.class);
        var intent = baseModel.intent();
        RequestedProblemSpecification requested = plan.requestedSpecification();
        var specification = new GenerationSpecification(
                requested != null && requested.questionType() != null ? requested.questionType() : intent.questionType(),
                requested != null && requested.difficulty() != null ? requested.difficulty() : intent.difficulty(),
                intent.evaluationArea(), java.util.List.of(), true);
        var command = new ProblemGenerationCommand(plan.requestId(), java.util.UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, specification, baseModel.curriculum(),
                java.util.List.of(new GenerationReference(GenerationReferenceRole.ORIGIN,
                        baseVersion.getSourceQuestionId(), baseSnapshot, baseModel)), java.util.List.of());
        ProblemCandidateDraft candidate = port.generate(command);
        if (candidate == null || candidate.semanticModel() == null)
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
        var result = processingService.process(new CandidateProcessingRequest(ownerTeacherId, plan.sessionId(),
                baseVersion.getId(), AuthoringOperationType.AI_MODIFY, VerificationOperationType.EDIT, candidate,
                new VerificationExpectation(candidate.snapshot().metadata().questionType(), candidate.snapshot().metadata().difficulty(),
                        null, candidate.snapshot().metadata().evaluationArea(), java.util.List.of(),
                        candidate.snapshot().assets().stream().map(a -> a.assetKey()).toList()),
                new EditVerificationContext(baseSnapshot, plan.instructions(), plan.requestedTargets(), plan.dependentTargets(), plan.protectedTargets()),
                "확정된 구조 재생성 실행"));
        return new ProblemModificationExecutionResult(result.versionId(), SemanticEditMode.STRUCTURAL_REGENERATION,
                new ProblemSemanticDiff(java.util.List.of(), java.util.Set.of(SemanticImpactArea.STEM, SemanticImpactArea.CHOICES,
                        SemanticImpactArea.STEPS, SemanticImpactArea.ANSWERS, SemanticImpactArea.EXPLANATION,
                        SemanticImpactArea.LEARNING_GUIDE, SemanticImpactArea.RUBRICS, SemanticImpactArea.ASSETS), true,
                        true), result.promoted(), false);
    }
}
