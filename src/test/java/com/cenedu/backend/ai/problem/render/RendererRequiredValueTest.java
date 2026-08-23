package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RendererRequiredValueTest {
    private DiagramStyle style() { return new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12); }
    private DiagramViewport viewport() { return new DiagramViewport(400, 240, 16); }
    @Test void numberLineRequiresResolvedRange() {
        var spec = new NumberLineDiagramSpecV1(1, "N", DiagramKind.NUMBER_LINE, viewport(), style(), "MIN", "MAX", "STEP", List.of(), List.of(), false, false);
        assertThatThrownBy(() -> new NumberLineSvgRenderer().render(spec, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void planeGeometryRequiresResolvedPoint() {
        var spec = new PlaneGeometryDiagramSpecV1(1, "P", DiagramKind.PLANE_GEOMETRY, viewport(), style(), List.of(new PlanePointSpec("A", "X", "Y", "A")), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> new PlaneGeometrySvgRenderer().render(spec, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void solidGeometryRequiresResolvedDimension() {
        var spec = new SolidGeometryDiagramSpecV1(1, "S", DiagramKind.SOLID_GEOMETRY, viewport(), style(), SolidGeometryKind.RECTANGULAR_PRISM, "W", "D", "H", null, null, null, List.of());
        assertThatThrownBy(() -> new SolidGeometrySvgRenderer().render(spec, Map.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
