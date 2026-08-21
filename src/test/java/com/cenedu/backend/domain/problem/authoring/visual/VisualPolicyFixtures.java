package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties;
import com.cenedu.backend.global.common.enums.QuestionType;
import java.util.*;

final class VisualPolicyFixtures {
    static ProblemVisualAuthoringProperties properties() {
        return new ProblemVisualAuthoringProperties(true, Set.of(DiagramKind.values()),
                Set.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_INPUT), 1);
    }
    static ProblemSemanticModelV1 model(QuestionType type, boolean required, Set<DiagramKind> kinds) {
        var diagrams = kinds.stream().map(k -> new DataTableDiagramSpecV1(1, "T", DiagramKind.DATA_TABLE,
                new DiagramViewport(400, 240, 16), new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                List.of("r"), List.of("c"), List.of(new TableCellSpec(0, 0, null, "x")), Set.of())).map(x -> (DiagramSpecV1)x).toList();
        var intent = new SemanticProblemIntent(type, "low", null, "strategy", "TARGET", 1, required);
        var presentation = new SemanticPresentationPlan("다음 표", List.of(), List.of(), "설명", null, List.of());
        return new ProblemSemanticModelV1(1, new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "a", "b", "c"),
                intent, List.of(), List.of(), List.of(), presentation, diagrams, List.of());
    }
    private VisualPolicyFixtures() {}
}
