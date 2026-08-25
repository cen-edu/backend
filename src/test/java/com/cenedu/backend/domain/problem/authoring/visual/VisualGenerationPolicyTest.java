package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualGenerationPolicyTest {
    @Test void noneModeWithoutDiagramPasses() { new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(VisualGenerationRequirement.none(), VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, false, Set.of())); }
    @Test void autoModeWithoutRequiredVisualAndDiagramPasses() { new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(new VisualGenerationRequirement(VisualGenerationMode.AUTO, VisualReferenceKind.NONE), VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, false, Set.of())); }
    @Test void productionAllowlistRejectsPlaneGeometry() { var production = new com.cenedu.backend.domain.problem.config.ProblemVisualAuthoringProperties(true, Set.of(DiagramKind.COORDINATE_GRAPH, DiagramKind.DATA_TABLE), Set.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_INPUT), 1); assertThatThrownBy(() -> new VisualGenerationPolicy(production).validate(new VisualGenerationRequirement(VisualGenerationMode.AUTO, VisualReferenceKind.PLANE_GEOMETRY), VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.PLANE_GEOMETRY)))).isInstanceOf(VisualPolicyViolationException.class); }
    @Test void autoModeRejectsTwoDiagrams() { assertThatThrownBy(() -> new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(new VisualGenerationRequirement(VisualGenerationMode.AUTO, VisualReferenceKind.DATA_TABLE), VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.DATA_TABLE, DiagramKind.COORDINATE_GRAPH)))).isInstanceOf(VisualPolicyViolationException.class); }
    @Test void preserveOriginAllowsMatchingDataTable() { new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(new VisualGenerationRequirement(VisualGenerationMode.PRESERVE_ORIGIN, VisualReferenceKind.DATA_TABLE), VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.DATA_TABLE))); }
    @Test void preserveOriginRejectsUnknownFigure() { assertThatThrownBy(() -> new VisualGenerationRequirement(VisualGenerationMode.PRESERVE_ORIGIN, VisualReferenceKind.UNKNOWN_FIGURE)).isInstanceOf(IllegalArgumentException.class); }
    @Test void noneModeRejectsDiagram() {
        var model = VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.DATA_TABLE));
        var props = VisualPolicyFixtures.properties();
        assertThatThrownBy(() -> new VisualGenerationPolicy(props).validate(VisualGenerationRequirement.none(), model))
                .isInstanceOf(VisualPolicyViolationException.class);
    }
    @Test void preserveOriginRejectsDifferentKind() {
        var model = VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.COORDINATE_GRAPH));
        var requirement = new VisualGenerationRequirement(VisualGenerationMode.PRESERVE_ORIGIN, VisualReferenceKind.NUMBER_LINE);
        assertThatThrownBy(() -> new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(requirement, model))
                .isInstanceOf(VisualPolicyViolationException.class);
    }
    @Test void autoAllowsOneConfiguredDiagram() {
        var model = VisualPolicyFixtures.model(QuestionType.MULTIPLE_CHOICE, true, Set.of(DiagramKind.DATA_TABLE));
        new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(
                new VisualGenerationRequirement(VisualGenerationMode.AUTO, VisualReferenceKind.DATA_TABLE), model);
    }
    @Test void stepFillVisualRequirementIsRejected() {
        var model = VisualPolicyFixtures.model(QuestionType.STEP_FILL, true, Set.of(DiagramKind.DATA_TABLE));
        assertThatThrownBy(() -> new VisualGenerationPolicy(VisualPolicyFixtures.properties()).validate(
                new VisualGenerationRequirement(VisualGenerationMode.AUTO, VisualReferenceKind.DATA_TABLE), model))
                .isInstanceOf(VisualPolicyViolationException.class);
    }
}
