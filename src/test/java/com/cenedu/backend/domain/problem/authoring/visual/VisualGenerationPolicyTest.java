package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VisualGenerationPolicyTest {
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
}
