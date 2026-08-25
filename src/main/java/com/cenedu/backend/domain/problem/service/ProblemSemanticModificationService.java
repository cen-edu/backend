package com.cenedu.backend.domain.problem.service;

import com.cenedu.backend.domain.problem.authoring.candidate.*;
import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.MaterializedProblem;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.authoring.semantic.persistence.ProblemSemanticDocumentCodec;
import com.cenedu.backend.domain.problem.authoring.verification.*;
import com.cenedu.backend.domain.problem.entity.ProblemAuthoringVersion;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Service;

/** 확인된 semantic patch를 서버에서만 적용하고 기존 후보 검증 경로로 보낸다. */
@Service
public class ProblemSemanticModificationService {
    private final ProblemAuthoringJsonCodec jsonCodec;
    private final ProblemSemanticMaterializer materializer;
    private final ProblemCandidateProcessingService processingService;
    private final ProblemSemanticPatchApplier applier;
    private final ProblemSemanticDiffFactory diffFactory;
    private final ProblemSemanticDocumentCodec semanticCodec =
            new ProblemSemanticDocumentCodec(new tools.jackson.databind.ObjectMapper());

    public ProblemSemanticModificationService(ProblemAuthoringJsonCodec jsonCodec,
            ProblemSemanticMaterializer materializer,
            ProblemCandidateProcessingService processingService) {
        this.jsonCodec = jsonCodec;
        this.materializer = materializer;
        this.processingService = processingService;
        this.applier = new ProblemSemanticPatchApplier(new ProblemSemanticPatchClassifier(), materializer);
        this.diffFactory = new ProblemSemanticDiffFactory();
    }

    /** PASSED Version을 기준으로 patch를 적용하고 검증 후보를 생성한다. */
    public ProblemModificationExecutionResult apply(long ownerTeacherId, long sessionId,
            ProblemAuthoringVersion baseVersion, ProblemSemanticPatch patch) {
        if (baseVersion == null || patch == null) throw new IllegalArgumentException("semantic modification 필수값이 누락되었습니다.");
        if (!java.util.Objects.equals(baseVersion.getId(), patch.baseVersionId()))
            throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
        if (baseVersion.getSemanticModel() == null)
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_UNSUPPORTED);
        ProblemSemanticModelV1 baseModel = semanticCodec.readSemanticModel(baseVersion.getSemanticModel());
        ProblemSemanticModelV1 changed;
        try {
            changed = applier.apply(baseModel, patch);
        } catch (SemanticPatchConflictException e) {
            throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE,
                    "현재 문항의 값이 patch가 기대한 값과 다릅니다: " + e.path()
                            + " (기대값=" + e.expected() + ", 실제값=" + e.actual() + ")");
        } catch (IllegalArgumentException e) {
            // patch가 mode의 불변식을 어긴 경우다. 서버 결함이 아니라 Agent가 만든 요청이
            // 규칙에 맞지 않는 상황이므로, 일반 예외로 흘려보내 500이 되게 두지 않는다.
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_EDIT_REJECTED, e.getMessage());
        }
        MaterializedProblem baseMaterialized = materializer.materialize(baseModel);
        MaterializedProblem materialized = materializer.materialize(changed);
        if (patch.mode() == SemanticEditMode.CHOICE_REORDER) {
            requireSameChoiceSet(baseMaterialized, materialized);
            requireSameCorrectChoice(baseMaterialized, materialized);
        }
        if (patch.mode() == SemanticEditMode.PRESENTATIONAL_PATCH) {
            if (!java.util.Objects.equals(baseMaterialized.report().resolvedValues(), materialized.report().resolvedValues())
                    || !java.util.Objects.equals(baseMaterialized.snapshot().answerUnits(), materialized.snapshot().answerUnits()))
                throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID);
            boolean styleOrLabelOnly = patch.operations().stream().allMatch(operation ->
                    operation.type() == SemanticPatchOperationType.SET_DIAGRAM_STYLE
                            || operation.type() == SemanticPatchOperationType.SET_LABEL_TEXT);
            if (!styleOrLabelOnly && !java.util.Objects.equals(baseModel.diagrams(), changed.diagrams()))
                throw new BusinessException(ErrorCode.PROBLEM_DIAGRAM_RENDER_FAILED);
            if (styleOrLabelOnly) {
                java.util.Set<String> changedAssets = new java.util.HashSet<>();
                for (int i = 0; i < baseModel.diagrams().size(); i++) {
                    if (!java.util.Objects.equals(baseModel.diagrams().get(i), changed.diagrams().get(i)))
                        changedAssets.add(baseModel.diagrams().get(i).assetKey());
                }
                java.util.Set<String> targetedAssets = patch.operations().stream()
                        .map(operation -> operation.path().split("/"))
                        .filter(parts -> parts.length > 2 && "diagrams".equals(parts[1]))
                        .map(parts -> parts[2]).collect(java.util.stream.Collectors.toSet());
                if (!targetedAssets.containsAll(changedAssets))
                    throw new BusinessException(ErrorCode.PROBLEM_DIAGRAM_RENDER_FAILED);
                for (int i = 0; i < baseModel.diagrams().size(); i++) {
                    String assetKey = baseModel.diagrams().get(i).assetKey();
                    if (!targetedAssets.contains(assetKey)
                            && !java.util.Objects.equals(semanticCodec.canonicalHash(baseModel.diagrams().get(i)),
                            semanticCodec.canonicalHash(changed.diagrams().get(i))))
                        throw new BusinessException(ErrorCode.PROBLEM_DIAGRAM_RENDER_FAILED);
                }
            }
        }
        QuestionSnapshotV1 baseSnapshot = jsonCodec.read(baseVersion.getSnapshot(), QuestionSnapshotV1.class);
        ProblemCandidateDraft candidate = new ProblemCandidateDraft(patch.requestId(), materialized.snapshot(),
                materialized.assetPlans(), changed, new CandidateProvenance(CandidateSourceType.AI_MODIFY, null, java.util.List.of()));
        var result = processingService.process(new CandidateProcessingRequest(ownerTeacherId, sessionId,
                baseVersion.getId(), AuthoringOperationType.AI_MODIFY, VerificationOperationType.EDIT, candidate,
                new VerificationExpectation(materialized.snapshot().metadata().questionType(),
                        materialized.snapshot().metadata().difficulty(), null,
                        materialized.snapshot().metadata().evaluationArea(), java.util.List.of(),
                        materialized.snapshot().assets().stream().map(a -> a.assetKey()).toList()),
                new EditVerificationContext(baseSnapshot, java.util.List.of(), java.util.List.of(), java.util.List.of(), java.util.List.of()),
                "확정된 semantic patch 실행"));
        return new ProblemModificationExecutionResult(result.versionId(), patch.mode(),
                diffFactory.create(baseModel, changed, patch.mode()), result.promoted(), false);
    }

    /** 순서만 바뀌었으므로 보기 본문의 집합은 그대로여야 한다. */
    private void requireSameChoiceSet(MaterializedProblem before, MaterializedProblem after) {
        if (!java.util.Objects.equals(sortedChoiceContents(before), sortedChoiceContents(after))) {
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID,
                    "보기 순서 변경이 보기 내용을 바꿨습니다.");
        }
    }

    /**
     * 순서가 바뀌어도 정답은 같은 보기를 가리켜야 한다.
     *
     * <p>정답은 choiceKey(C1·C2…)로 저장되고 그 키는 순서에서 나오므로, 재정렬 후 정답 키는
     * 반드시 달라진다. 따라서 키가 아니라 그 키가 가리키는 본문을 비교해야 정답이 다른 보기로
     * 옮겨 가지 않았는지 확인할 수 있다.
     */
    private void requireSameCorrectChoice(MaterializedProblem before, MaterializedProblem after) {
        if (!java.util.Objects.equals(correctChoiceContent(before), correctChoiceContent(after))) {
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID,
                    "보기 순서 변경이 정답을 다른 보기로 옮겼습니다.");
        }
    }

    private java.util.List<String> sortedChoiceContents(MaterializedProblem problem) {
        return problem.snapshot().choices().stream()
                .map(com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice::content)
                .sorted().toList();
    }

    private String correctChoiceContent(MaterializedProblem problem) {
        var units = problem.snapshot().answerUnits();
        if (units.isEmpty()) {
            throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID, "정답 단위가 없습니다.");
        }
        String choiceKey = units.getFirst().answerRaw();
        return problem.snapshot().choices().stream()
                .filter(choice -> choice.choiceKey().equals(choiceKey))
                .map(com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice::content)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.PROBLEM_SEMANTIC_MODEL_INVALID,
                        "정답이 가리키는 보기를 찾을 수 없습니다."));
    }
}
