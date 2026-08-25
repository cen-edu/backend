package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ResolvedValueSvgConsistencyTest {
    private DiagramStyle style() { return new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12); }
    private CoordinateGraphDiagramSpecV1 graph() {
        return new CoordinateGraphDiagramSpecV1(1, "G", DiagramKind.COORDINATE_GRAPH,
                new DiagramViewport(400, 240, 16), style(), "XMIN", "XMAX", "YMIN", "YMAX", "XT", "YT",
                List.of(), List.of(), List.of(), List.of(new CoordinateFunctionSpec("F", CoordinateFunctionKind.DIRECT_PROPORTION, "K", "y=kx")));
    }
    private Map<String, SemanticResolvedValue> values(String coefficient) {
        return Map.of("XMIN", value("-5"), "XMAX", value("5"), "YMIN", value("-5"), "YMAX", value("5"),
                "XT", value("1"), "YT", value("1"), "K", value(coefficient));
    }
    private SemanticResolvedValue value(String value) { return new SemanticResolvedValue(SemanticValueType.INTEGER, value, null); }
    @Test void resolvedCoefficientChangesRenderedSvgChecksum() {
        var renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());
        var first = renderer.render(graph(), new DiagramRenderContext(values("1")));
        var second = renderer.render(graph(), new DiagramRenderContext(values("3")));
        assertThat(first.sha256()).isNotEqualTo(second.sha256());
    }
    @Test void missingRequiredResolvedValueDoesNotUseFallback() {
        assertThatThrownBy(() -> new CoordinateGraphSvgRenderer().render(graph(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
