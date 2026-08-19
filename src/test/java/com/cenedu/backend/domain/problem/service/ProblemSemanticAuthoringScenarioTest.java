package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

    @ParameterizedTest(name = "{0}")
    @MethodSource("semanticScenarios")
    void ten_authoring_scenarios_keep_answer_free_impact_contract(String name, SemanticEditMode mode,
            Set<SemanticImpactArea> expectedAreas) {
        var diff = new ProblemSemanticDiff(List.of(), expectedAreas, mode == SemanticEditMode.STRUCTURAL_REGENERATION, true);
        var result = new ProblemModificationExecutionResult(100L, mode, diff, true, false);
        assertThat(result.diff().impactedAreas()).containsAll(expectedAreas);
        assertThat(result.diff().parameterChanges()).isEmpty();
    }

    static java.util.stream.Stream<Arguments> semanticScenarios() {
        Set<SemanticImpactArea> geometry = Set.of(SemanticImpactArea.STEM, SemanticImpactArea.ANSWERS,
                SemanticImpactArea.EXPLANATION, SemanticImpactArea.ASSETS);
        return java.util.stream.Stream.of(
                Arguments.of("number-line marked point", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("coordinate point", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("direct proportion", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("inverse proportion", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("triangle side", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("circle radius", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("rectangular prism dimensions", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("data table cell", SemanticEditMode.PARAMETRIC_PATCH, geometry),
                Arguments.of("essay criterion", SemanticEditMode.PRESENTATIONAL_PATCH, Set.of(SemanticImpactArea.RUBRICS)),
                Arguments.of("question type change", SemanticEditMode.STRUCTURAL_REGENERATION,
                        EnumSet.allOf(SemanticImpactArea.class)));
    }
}
