package com.cenedu.backend.domain.problem.authoring.diagram;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinateGraphOptionalTickContractTest {
    @Test
    void nullableSchemaTicksAreAcceptedAndRendererUsesDeterministicFallback() {
        var viewport = new DiagramViewport(640, 240, 16);
        var style = new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12);
        var spec = new CoordinateGraphDiagramSpecV1(1, "GRAPH", DiagramKind.COORDINATE_GRAPH,
                viewport, style, "XMIN", "XMAX", "YMIN", "YMAX", null, null,
                List.of(), List.of(), List.of(), List.of());
        var values = Map.of("XMIN", value(-5), "XMAX", value(5), "YMIN", value(-5), "YMAX", value(5));

        assertThatCode(() -> new DiagramSpecValidator().validate(spec, values)).doesNotThrowAnyException();
        var svg = new com.cenedu.backend.ai.problem.render.ProblemDiagramRenderer(
                new com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer()).render(spec,
                new DiagramRenderContext(values)).svg();
        org.assertj.core.api.Assertions.assertThat(svg).contains("<line");
    }

    private SemanticResolvedValue value(int value) {
        return new SemanticResolvedValue(SemanticValueType.INTEGER, Integer.toString(value), null);
    }
}
