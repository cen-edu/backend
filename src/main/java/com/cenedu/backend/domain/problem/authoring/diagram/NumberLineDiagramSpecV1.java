package com.cenedu.backend.domain.problem.authoring.diagram;

import java.util.*;

public record NumberLineDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind, DiagramViewport viewport,
                                      DiagramStyle style, String minKey, String maxKey, String tickIntervalKey,
                                      List<NumberLinePointSpec> points, List<NumberLineIntervalSpec> intervals,
                                      boolean startArrow, boolean endArrow) implements DiagramSpecV1 {
    public NumberLineDiagramSpecV1 {
        points = List.copyOf(points);
        intervals = List.copyOf(intervals);
    }
}
