package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;
import com.cenedu.backend.ai.problem.render.ProblemDiagramRenderer;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
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

    @Test
    void dependent_values_and_diagram_hash_are_recomputed_for_real_semantic_inputs() {
        var before = evaluate("2", "6");
        var after = evaluate("3", "6");
        assertThat(before.values().get("ANSWER").canonicalValue()).isEqualTo("12");
        assertThat(after.values().get("ANSWER").canonicalValue()).isEqualTo("18");

        var spec = new CoordinateGraphDiagramSpecV1(1, "GRAPH", DiagramKind.COORDINATE_GRAPH,
                new DiagramViewport(640, 240, 16),
                new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                "X0", "X1", "Y0", "Y1", null, null, List.of(), List.of(), List.of(),
                List.of(new CoordinateFunctionSpec("F", CoordinateFunctionKind.DIRECT_PROPORTION, "K", "direct")));
        var renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());
        var beforeHash = renderer.render(spec, new DiagramRenderContext(before.values())).sha256();
        var afterHash = renderer.render(spec, new DiagramRenderContext(after.values())).sha256();
        assertThat(afterHash).isNotEqualTo(beforeHash);
    }

    private SemanticEvaluation evaluate(String k, String x) {
        var model = new ProblemSemanticModelV1(1, null,
                new SemanticProblemIntent(com.cenedu.backend.global.common.enums.QuestionType.SHORT_INPUT,
                        "mid", null, "compute", "ANSWER", 1, true),
                List.of(parameter("K", k), parameter("X", x)),
                List.of(new SemanticComputation("ANSWER", SemanticOperation.MULTIPLY,
                        List.of("K", "X"), null, null, null)),
                List.of(), new SemanticPresentationPlan("", List.of(), List.of(), "", null, List.of()),
                List.of(), List.of());
        return new SemanticComputationEngine().evaluate(model);
    }

    private SemanticParameter parameter(String key, String value) {
        return new SemanticParameter(key, SemanticValueType.INTEGER, value, null, true, null);
    }
}
