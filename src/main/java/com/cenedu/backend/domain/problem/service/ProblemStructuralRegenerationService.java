package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.generation.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemGenerationPort;
import com.cenedu.backend.domain.problem.authoring.semantic.extraction.SemanticExtractionStatus;
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
    private ProblemSemanticExtractionService semanticExtractionService;

    public ProblemStructuralRegenerationService(ObjectProvider<ProblemGenerationPort> generationPortProvider,
            ProblemCandidateProcessingService processingService, ProblemAuthoringJsonCodec jsonCodec,
            ProblemAuthoringVersionRepository versionRepository) {
        this.generationPortProvider = generationPortProvider;
        this.processingService = processingService;
        this.jsonCodec = jsonCodec;
        this.versionRepository = versionRepository;
    }

    /** 재생성 후보에 semantic model을 붙이기 위한 추출 경계를 선택적으로 연결한다. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setSemanticExtractionService(ProblemSemanticExtractionService service) {
        this.semanticExtractionService = service;
    }

    /** 현재 문항을 ORIGIN으로만 전달해 구조 변경 후보를 생성·검증한다. */
    public ProblemModificationExecutionResult regenerate(long ownerTeacherId,
            ProblemAuthoringVersion baseVersion, ProblemEditExecutionPlan plan,
            ProblemSemanticModelV1 baseModel) {
        return regenerate(ownerTeacherId, baseVersion, plan, baseModel, java.util.List.of());
    }

    /** 은행 교체 미스 시 검색한 유사 문항을 EXAMPLE로 함께 전달한다. */
    public ProblemModificationExecutionResult regenerate(long ownerTeacherId,
            ProblemAuthoringVersion baseVersion, ProblemEditExecutionPlan plan,
            ProblemSemanticModelV1 baseModel, java.util.List<GenerationReference> examples) {
        ProblemGenerationPort port = generationPortProvider.getIfAvailable();
        if (port == null) throw new BusinessException(ErrorCode.PROBLEM_AI_PORT_NOT_CONFIGURED);
        QuestionSnapshotV1 baseSnapshot = jsonCodec.read(baseVersion.getSnapshot(), QuestionSnapshotV1.class);
        var intent = baseModel.intent();
        RequestedProblemSpecification requested = plan.requestedSpecification();
        var specification = new GenerationSpecification(
                requested != null && requested.questionType() != null ? requested.questionType() : intent.questionType(),
                requested != null && requested.difficulty() != null ? requested.difficulty() : intent.difficulty(),
                intent.evaluationArea(), java.util.List.of(), true, visualRequirement(baseModel));
        java.util.List<GenerationReference> references = new java.util.ArrayList<>();
        references.add(new GenerationReference(GenerationReferenceRole.ORIGIN,
                baseVersion.getSourceQuestionId(), baseSnapshot, baseModel));
        if (examples != null) references.addAll(examples);
        var command = new ProblemGenerationCommand(plan.requestId(), java.util.UUID.randomUUID(),
                GenerationPurpose.GENERAL_LEARNING_SHORTAGE, specification, baseModel.curriculum(),
                references, java.util.List.of(),
                null, editInstruction(plan));
        ProblemCandidateDraft candidate = port.generate(command);
        if (candidate == null) throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
        candidate = withSemanticModel(candidate, baseModel);
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

    /**
     * 재생성 후보에 semantic model이 없으면 후보 snapshot에서 한 번 추출해 붙인다.
     *
     * <p>{@link #visualRequirement}가 원본 기준으로 계산되므로, 도형이 없는 문항(대부분의
     * 객관식·주관식·빈칸형·서술형)은 mode=NONE으로 나가 SpringAiProblemGenerationAdapter가
     * semantic model을 만들지 않는 NonSemanticProblemGenerationPipeline으로 보낸다. 그 결과를
     * 그대로 실패로 처리하면 도형 없는 문항의 구조 재생성이 항상
     * PROBLEM_SEMANTIC_MODEL_INVALID로 죽는다 — 난이도·문항 유형 변경과 문제 교체가 전부
     * 여기로 들어오므로 사실상 수정 기능 전체가 막힌다. 추출까지 실패한 경우에만 원래대로
     * 실패시킨다. 후보 검증(validateSemanticCandidate)이 semantic model 있는 부모의 AI_MODIFY
     * 후보에는 model을 요구하므로 여기서 조용히 없는 채로 넘길 수는 없다.
     */
    private ProblemCandidateDraft withSemanticModel(ProblemCandidateDraft candidate,
            ProblemSemanticModelV1 baseModel) {
        if (candidate.semanticModel() != null) return candidate;
        if (semanticExtractionService == null)
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
        var extraction = semanticExtractionService.extractForCandidate(
                baseModel.curriculum(), candidate.snapshot());
        if (extraction.status() != SemanticExtractionStatus.EXTRACTED
                || extraction.semanticModel() == null)
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
        return new ProblemCandidateDraft(candidate.requestId(), candidate.snapshot(),
                candidate.assetPlans(), extraction.semanticModel(), candidate.provenance());
    }

    private VisualGenerationRequirement visualRequirement(ProblemSemanticModelV1 baseModel) {
        if (!baseModel.intent().visualRequired() || baseModel.diagrams().isEmpty()) {
            return VisualGenerationRequirement.none();
        }
        return new VisualGenerationRequirement(VisualGenerationMode.PRESERVE_ORIGIN,
                VisualReferenceKind.fromDiagramKind(baseModel.diagrams().getFirst().kind()));
    }
}
