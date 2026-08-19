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
        ProblemSemanticModelV1 changed = applier.apply(baseModel, patch);
        MaterializedProblem baseMaterialized = materializer.materialize(baseModel);
        MaterializedProblem materialized = materializer.materialize(changed);
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
}
