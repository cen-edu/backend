package com.cenedu.backend.domain.problem.authoring.diagram;

import java.util.*;

public record PlaneGeometryDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind, DiagramViewport viewport,
                                         DiagramStyle style, List<PlanePointSpec> points,
                                         List<PlaneSegmentSpec> segments, List<PlaneAngleSpec> angles,
                                         List<PlanePolygonSpec> polygons, List<PlaneCircleSpec> circles,
                                         List<PlaneArcSpec> arcs,
                                         List<PlaneMeasurementSpec> measurements) implements DiagramSpecV1 {
    public PlaneGeometryDiagramSpecV1 {
        points = List.copyOf(points);
        segments = List.copyOf(segments);
        angles = List.copyOf(angles);
        polygons = List.copyOf(polygons);
        circles = List.copyOf(circles);
        arcs = List.copyOf(arcs);
        measurements = List.copyOf(measurements);
    }
}
