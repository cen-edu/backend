package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.verification.*;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationMode;
import com.cenedu.backend.domain.problem.authoring.visual.VisualGenerationRequirement;
import com.cenedu.backend.domain.problem.authoring.visual.VisualReferenceKind;
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
                intent.evaluationArea(), java.util.List.of(), true, visualRequirement(baseModel));
        var command = new ProblemGenerationCommand(plan.requestId(), java.util.UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, specification, baseModel.curriculum(),
                java.util.List.of(new GenerationReference(GenerationReferenceRole.ORIGIN,
                        baseVersion.getSourceQuestionId(), baseSnapshot, baseModel)), java.util.List.of(),
                null, editInstruction(plan));
        ProblemCandidateDraft candidate = port.generate(command);
        if (candidate == null || candidate.semanticModel() == null)
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
        // generation port는 항상 AI_GENERATE 출처로 후보를 만든다. 이 서비스는 그 결과를
        // AuthoringOperationType.AI_MODIFY Version으로 등록하므로, validateSourceType의
        // operationType-sourceType 일치 검사를 통과하도록 출처를 이 흐름에 맞게 다시 붙인다.
        candidate = new ProblemCandidateDraft(candidate.requestId(), candidate.snapshot(), candidate.assetPlans(),
                candidate.semanticModel(), new CandidateProvenance(CandidateSourceType.AI_MODIFY,
                        candidate.provenance().sourceQuestionId(), candidate.provenance().referenceQuestionIds()));
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

    /**
     * 원본이 도형을 갖고 있으면 같은 도형 종류를 유지한 채 값만 새로 만들도록 지시한다.
     *
     * <p>이 값을 채우지 않으면 {@link GenerationSpecification}의 기본값(mode=NONE)이 그대로 나가
     * {@code SpringAiProblemGenerationAdapter}가 semantic model을 만들지 않는
     * {@code NonSemanticProblemGenerationPipeline}으로 보낸다. 구조 재생성은 이미 semantic model이
     * 있는 문항에서만 일어나므로, 그 결과가 다시 semantic model 없이 돌아오면 이후 검증에서
     * PROBLEM_SEMANTIC_MODEL_INVALID로 막힌다.
     */
    /**
     * 교사가 실제로 요청한 변경 내용을 재생성 LLM에 전달한다.
     *
     * <p>STRUCTURAL_REGENERATION은 semanticPatch.operations와 instructionDeltas가 항상 비어
     * 있어야 하는 mode라(가드·분류기 규칙), 이 시점에는 교사의 원 요청을 담은 자유 텍스트가
     * {@link com.cenedu.backend.domain.problem.authoring.edit.semantic.ProblemSemanticPatch#assistantMessage()}에만
     * 남아 있다. 이걸 넘기지 않으면 재생성 프롬프트는 origin과 "비슷하거나 더 어려운" 문제를
     * 만들라는 범용 지시만 받아서, 교사가 요청한 구체적인 값 변경을 반영하지 못하고 원본과
     * 거의 동일한 후보를 다시 만들어낸다.
     */
    private String editInstruction(ProblemEditExecutionPlan plan) {
        return plan.semanticPatch() == null ? null : plan.semanticPatch().assistantMessage();
    }

    private VisualGenerationRequirement visualRequirement(ProblemSemanticModelV1 baseModel) {
        if (!baseModel.intent().visualRequired() || baseModel.diagrams().isEmpty()) {
            return VisualGenerationRequirement.none();
        }
        return new VisualGenerationRequirement(VisualGenerationMode.PRESERVE_ORIGIN,
                VisualReferenceKind.fromDiagramKind(baseModel.diagrams().getFirst().kind()));
    }
}
