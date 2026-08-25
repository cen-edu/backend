package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** semantic 모델이 시각 생성 불변조건과 서버 allowlist를 만족하는지 판정한다. */
public final class VisualGenerationPolicy {
    private final ProblemVisualAuthoringProperties properties;

    public VisualGenerationPolicy(ProblemVisualAuthoringProperties properties) {
        this.properties = Objects.requireNonNull(properties, "visual authoring properties가 필요합니다.");
    }

    /** 생성 요구와 semantic 모델의 시각 조건을 모두 검증하고 위반을 한 번에 보고한다. */
    public void validate(VisualGenerationRequirement requirement, ProblemSemanticModelV1 model) {
        List<String> violations = new ArrayList<>();
        if (requirement == null) violations.add("visual generation requirement is missing");
        if (model == null) violations.add("semantic model is missing");
        if (!violations.isEmpty()) throw new VisualPolicyViolationException(violations);

        boolean visualRequired = model.intent() != null && model.intent().visualRequired();
        List<DiagramSpecV1> diagrams = model.diagrams() == null ? List.of() : model.diagrams();
        int diagramCount = diagrams.size();
        VisualGenerationMode mode = requirement.mode();

        if (!properties.enabled() && mode != VisualGenerationMode.NONE) {
            violations.add("visual authoring is disabled");
        }
        if (model.intent() == null) {
            violations.add("semantic intent is missing");
        }
        if (mode == VisualGenerationMode.NONE && visualRequired) {
            violations.add("NONE mode cannot satisfy visualRequired=true");
        }
        if (mode == VisualGenerationMode.NONE && diagramCount > 0) {
            violations.add("NONE mode cannot contain diagrams");
        }
        if (mode == VisualGenerationMode.AUTO && !visualRequired && diagramCount > 0) {
            violations.add("AUTO mode cannot contain diagrams when visualRequired=false");
        }
        if (mode == VisualGenerationMode.AUTO && visualRequired && diagramCount != 1) {
            violations.add("AUTO visualRequired=true requires exactly one diagram");
        }
        if (mode == VisualGenerationMode.REQUIRED && (!visualRequired || diagramCount != 1)) {
            violations.add("REQUIRED mode requires visualRequired=true and exactly one diagram");
        }
        if (mode == VisualGenerationMode.PRESERVE_ORIGIN && !visualRequired) {
            violations.add("PRESERVE_ORIGIN requires visualRequired=true");
        }
        if (mode == VisualGenerationMode.PRESERVE_ORIGIN && diagramCount != 1) {
            violations.add("PRESERVE_ORIGIN requires exactly one diagram");
        }
        if (diagramCount > properties.maxAssetsPerQuestion()) {
            violations.add("diagram count exceeds maxAssetsPerQuestion");
        }

        QuestionType questionType = model.intent() == null ? null : model.intent().questionType();
        if (visualRequired && !properties.allowedQuestionTypes().contains(questionType)) {
            violations.add("question type is not allowed for visual authoring");
        }

        for (DiagramSpecV1 spec : diagrams) {
            if (spec == null || spec.kind() == null) {
                violations.add("diagram kind is missing");
                continue;
            }
            DiagramKind actualKind = spec.kind();
            if (!properties.allowedKinds().contains(actualKind)) {
                violations.add("diagram kind is not in the configured allowlist: " + actualKind);
            }
            if (mode == VisualGenerationMode.PRESERVE_ORIGIN
                    && !VisualReferenceKind.fromDiagramKind(actualKind).equals(requirement.requiredKind())) {
                violations.add("diagram kind does not match required origin visual kind");
            }
        }

        if (!violations.isEmpty()) throw new VisualPolicyViolationException(violations);
    }
}
