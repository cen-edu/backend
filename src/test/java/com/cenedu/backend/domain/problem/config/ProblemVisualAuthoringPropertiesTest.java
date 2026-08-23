package com.cenedu.backend.domain.problem.config;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemVisualAuthoringPropertiesTest {
    @Test void productionAllowlistContainsOnlyGraphAndTableByConfiguredInput() {
        var properties = new ProblemVisualAuthoringProperties(true,
                Set.of(DiagramKind.COORDINATE_GRAPH, DiagramKind.DATA_TABLE),
                Set.of(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_INPUT), 1);
        assertThat(properties.allowedKinds()).containsExactlyInAnyOrder(DiagramKind.COORDINATE_GRAPH, DiagramKind.DATA_TABLE);
        assertThat(properties.allowedQuestionTypes()).containsExactlyInAnyOrder(QuestionType.MULTIPLE_CHOICE, QuestionType.SHORT_INPUT);
    }
}
