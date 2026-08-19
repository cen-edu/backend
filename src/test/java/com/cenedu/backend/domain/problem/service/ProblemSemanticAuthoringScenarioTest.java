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

    @Test
    void geometry_and_table_scenarios_recompute_rendered_output() {
        var viewport = new DiagramViewport(640, 240, 16);
        var style = new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12);
        var renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());

        var line = new NumberLineDiagramSpecV1(1, "N", DiagramKind.NUMBER_LINE, viewport, style,
                "MIN", "MAX", "STEP", List.of(new NumberLinePointSpec("P", "P", "point", PointMarker.CLOSED_CIRCLE)),
                List.of(), true, true);
        var lineBefore = renderer.render(line, new DiagramRenderContext(values(Map.of("MIN", "-10", "MAX", "10", "STEP", "2", "P", "-2")))).sha256();
        var lineAfter = renderer.render(line, new DiagramRenderContext(values(Map.of("MIN", "-10", "MAX", "10", "STEP", "2", "P", "4")))).sha256();
        assertThat(lineAfter).isNotEqualTo(lineBefore);

        var plane = new PlaneGeometryDiagramSpecV1(1, "P", DiagramKind.PLANE_GEOMETRY, viewport, style,
                List.of(new PlanePointSpec("A", "AX", "AY", "A"), new PlanePointSpec("B", "BX", "BY", "B"),
                        new PlanePointSpec("C", "CX", "CY", "C")), List.of(), List.of(),
                List.of(new PlanePolygonSpec("T", List.of("A", "B", "C"), true, "triangle")), List.of(), List.of(), List.of());
        var planeBefore = renderer.render(plane, new DiagramRenderContext(values(Map.of("AX", "1", "AY", "1", "BX", "4", "BY", "1", "CX", "2", "CY", "3")))).sha256();
        var planeAfter = renderer.render(plane, new DiagramRenderContext(values(Map.of("AX", "1", "AY", "1", "BX", "5", "BY", "1", "CX", "2", "CY", "3")))).sha256();
        assertThat(planeAfter).isNotEqualTo(planeBefore);

        var solid = new SolidGeometryDiagramSpecV1(1, "S", DiagramKind.SOLID_GEOMETRY, viewport, style,
                SolidGeometryKind.CYLINDER, "W", "D", "H", "R", null, null,
                List.of(new SolidLabelSpec("radius", "R", "radius")));
        var solidSvg = renderer.render(solid, new DiagramRenderContext(values(Map.of("R", "5", "H", "10", "W", "8", "D", "4")))).svg();
        assertThat(solidSvg).contains("radius 5").contains("<ellipse");

        var table = new DataTableDiagramSpecV1(1, "T", DiagramKind.DATA_TABLE, viewport, style,
                List.of("row"), List.of("column"), List.of(new TableCellSpec(0, 0, "CELL", "fallback")),
                Set.of(new TableCellAddress(0, 0)));
        var tableSvg = renderer.render(table, new DiagramRenderContext(values(Map.of("CELL", "7")))).svg();
        assertThat(tableSvg).contains("row").contains("column").contains("7");
    }

    @Test
    void inverse_prism_and_rubric_scenarios_keep_dependent_contracts() {
        var viewport = new DiagramViewport(640, 240, 16);
        var style = new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12);
        var renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());
        var inverse = new CoordinateGraphDiagramSpecV1(1, "INV", DiagramKind.COORDINATE_GRAPH, viewport, style,
                "X0", "X1", "Y0", "Y1", null, null, List.of(), List.of(), List.of(),
                List.of(new CoordinateFunctionSpec("F", CoordinateFunctionKind.INVERSE_PROPORTION, "K", "inverse")));
        var inverseSvg = renderer.render(inverse, new DiagramRenderContext(values(Map.of(
                "X0", "-10", "X1", "10", "Y0", "-10", "Y1", "10", "K", "12")))).svg();
        assertThat(inverseSvg).contains("<path");

        var prism = new SolidGeometryDiagramSpecV1(1, "PRISM", DiagramKind.SOLID_GEOMETRY, viewport, style,
                SolidGeometryKind.PRISM, "W", "D", "H", null, null, null,
                List.of(new SolidLabelSpec("width", "W", "width"),
                        new SolidLabelSpec("depth", "D", "depth"),
                        new SolidLabelSpec("height", "H", "height")));
        var prismSvg = renderer.render(prism, new DiagramRenderContext(values(Map.of(
                "W", "3", "D", "4", "H", "5")))).svg();
        assertThat(prismSvg).contains("width 3").contains("depth 4").contains("height 5");

        var rubrics = List.of(new SemanticRubricTemplate("R1", 1, "근거", 40),
                new SemanticRubricTemplate("R2", 2, "계산", 60));
        assertThat(rubrics.stream().mapToInt(SemanticRubricTemplate::weightPercent).sum()).isEqualTo(100);
        assertThat(rubrics.toString()).doesNotContain("answerRaw", "answerNormalized");
    }

    private Map<String, SemanticResolvedValue> values(Map<String, String> source) {
        var result = new LinkedHashMap<String, SemanticResolvedValue>();
        source.forEach((key, value) -> result.put(key,
                new SemanticResolvedValue(SemanticValueType.INTEGER, value, null)));
        return result;
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
