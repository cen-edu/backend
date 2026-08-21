package com.cenedu.backend.domain.problem.authoring.diagram;

import java.util.*;

public record PlanePolygonSpec(String polygonKey, List<String> pointKeys, boolean filled, String labelTemplate) {
    public PlanePolygonSpec {
        pointKeys = List.copyOf(pointKeys);
    }
}
