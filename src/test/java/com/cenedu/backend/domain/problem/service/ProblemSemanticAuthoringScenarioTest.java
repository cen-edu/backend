package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import java.util.*;

class ProblemSemanticAuthoringScenarioTest {
    @Test
    void structural_question_type_change는_empty_patch와_재검증을_요구한다() {
        var diff = new ProblemSemanticDiff(List.of(),
                EnumSet.allOf(SemanticImpactArea.class), true, true);
        var result = new ProblemModificationExecutionResult(null,
                SemanticEditMode.STRUCTURAL_REGENERATION, diff, false, false);
        assertThat(result.mode()).isEqualTo(SemanticEditMode.STRUCTURAL_REGENERATION);
        assertThat(result.diff().structuralChange()).isTrue();
        assertThat(result.diff().parameterChanges()).isEmpty();
    }
}
