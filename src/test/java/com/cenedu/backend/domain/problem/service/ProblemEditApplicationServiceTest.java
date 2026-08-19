package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.domain.problem.dto.response.ProblemModificationPreviewResponse;
import org.junit.jupiter.api.Test;
import java.util.*;

class ProblemEditApplicationServiceTest {
    @Test
    void preview는_answer_free_parameter_diff만_노출한다() {
        var result = new ProblemModificationExecutionResult(103L, SemanticEditMode.PARAMETRIC_PATCH,
                new ProblemSemanticDiff(List.of(new SemanticValueChange("RADIUS", "3", "5", "cm", "cm")),
                        Set.of(SemanticImpactArea.STEM, SemanticImpactArea.ANSWERS, SemanticImpactArea.EXPLANATION,
                                SemanticImpactArea.ASSETS), false, true), true, false);
        var preview = ProblemModificationPreviewResponse.from(result);
        assertThat(preview.previewVersionId()).isEqualTo(103L);
        assertThat(preview.parameterChanges()).containsExactly(
                new com.cenedu.backend.domain.problem.dto.response.ProblemParameterChangeResponse("RADIUS", "3", "5", "cm", "cm"));
        assertThat(preview.toString()).doesNotContain("answerRaw", "semanticModel", "svg");
    }
}
