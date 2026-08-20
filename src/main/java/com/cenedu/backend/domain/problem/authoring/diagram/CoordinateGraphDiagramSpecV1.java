package com.cenedu.backend.domain.problem.authoring.diagram;

import java.util.*;

public record CoordinateGraphDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind,
                                           DiagramViewport viewport, DiagramStyle style, String xMinKey, String xMaxKey,
                                           String yMinKey, String yMaxKey, String xTickKey, String yTickKey,
                                           List<CoordinatePointSpec> points, List<CoordinateSegmentSpec> segments,
                                           List<CoordinateLineSpec> lines,
                                           List<CoordinateFunctionSpec> functions) implements DiagramSpecV1 {
    public CoordinateGraphDiagramSpecV1 {
        points = List.copyOf(points);
        segments = List.copyOf(segments);
        lines = List.copyOf(lines);
        functions = List.copyOf(functions);
    }
}
