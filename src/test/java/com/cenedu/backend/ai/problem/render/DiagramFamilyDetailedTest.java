package com.cenedu.backend.ai.problem.render;

import static org.assertj.core.api.Assertions.assertThat;
import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import java.util.*;
import org.junit.jupiter.api.Test;

class DiagramFamilyDetailedTest {
    private final DiagramViewport viewport = new DiagramViewport(640, 240, 16);
    private final DiagramStyle style = new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12);
    private final ProblemDiagramRenderer renderer = new ProblemDiagramRenderer(new SafeSvgSanitizer());

    @Test void numberLineUsesRangeTicksMarkersAndIntervals() {
        var spec = new NumberLineDiagramSpecV1(1, "N", DiagramKind.NUMBER_LINE, viewport, style, "MIN", "MAX", "STEP",
                List.of(new NumberLinePointSpec("P", "P", "point", PointMarker.CLOSED_CIRCLE)),
                List.of(new NumberLineIntervalSpec("I", "A", "B", true, false, "interval")), true, true);
        String svg = render(spec, Map.of("MIN", n(0), "MAX", n(10), "STEP", n(2), "P", n(4), "A", n(2), "B", n(8)));
        assertThat(svg).contains("stroke-width=\"2\"").contains("point").contains("<path");
    }

    @Test void coordinateGraphUsesBoundsAndSplitsInverseBranches() {
        var spec = new CoordinateGraphDiagramSpecV1(1, "C", DiagramKind.COORDINATE_GRAPH, viewport, style, "X0", "X1", "Y0", "Y1", null, null,
                List.of(new CoordinatePointSpec("P", "PX", "PY", "P", PointMarker.CLOSED_CIRCLE)), List.of(), List.of(),
                List.of(new CoordinateFunctionSpec("F", CoordinateFunctionKind.INVERSE_PROPORTION, "K", "inverse")));
        String svg = render(spec, Map.of("X0", n(-10), "X1", n(10), "Y0", n(-10), "Y1", n(10), "K", n(4), "PX", n(2), "PY", n(2)));
        assertThat(svg).contains("<path");
    }

    @Test void planeGeometryUsesPointKeysForPolygonAndSemanticLabels() {
        var spec = new PlaneGeometryDiagramSpecV1(1, "P", DiagramKind.PLANE_GEOMETRY, viewport, style,
                List.of(new PlanePointSpec("A", "AX", "AY", "A"), new PlanePointSpec("B", "BX", "BY", "B"), new PlanePointSpec("C", "CX", "CY", "C")),
                List.of(new PlaneSegmentSpec("AB", "A", "B", false, false)), List.of(), List.of(new PlanePolygonSpec("T", List.of("A", "B", "C"), true, "triangle")), List.of(), List.of(), List.of());
        String svg = render(spec, Map.of("AX", n(1), "AY", n(1), "BX", n(4), "BY", n(1), "CX", n(2), "CY", n(4)));
        assertThat(svg).contains("triangle").contains("<path").contains("<line");
    }

    @Test void solidGeometrySelectsCylinderAndUsesRadius() {
        var spec = new SolidGeometryDiagramSpecV1(1, "S", DiagramKind.SOLID_GEOMETRY, viewport, style, SolidGeometryKind.CYLINDER, "W", "D", "H", "R", null, null,
                List.of(new SolidLabelSpec("radius", "R", "radius")));
        String svg = render(spec, Map.of("W", n(8), "D", n(4), "H", n(10), "R", n(3)));
        assertThat(svg).contains("<ellipse").contains("radius 3");
    }

    @Test void dataTableUsesCellCoordinatesAndHighlight() {
        var spec = new DataTableDiagramSpecV1(1, "T", DiagramKind.DATA_TABLE, viewport, style, List.of("r0", "r1"), List.of("c0", "c1"),
                List.of(new TableCellSpec(1, 1, "VALUE", "fallback")), Set.of(new TableCellAddress(1, 1)));
        String svg = render(spec, Map.of("VALUE", n(42)));
        assertThat(svg).contains("r1").contains("c1").contains("42").contains("#FF0000");
    }

    private String render(DiagramSpecV1 spec, Map<String, SemanticResolvedValue> values) { return renderer.render(spec, new DiagramRenderContext(values)).svg(); }
    private static SemanticResolvedValue n(int value) { return new SemanticResolvedValue(SemanticValueType.INTEGER, Integer.toString(value), null); }
}
