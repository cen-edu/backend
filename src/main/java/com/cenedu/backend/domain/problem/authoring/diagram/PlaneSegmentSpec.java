package com.cenedu.backend.domain.problem.authoring.diagram;

public record PlaneSegmentSpec(String segmentKey, String startPointKey, String endPointKey, boolean startArrow,
                               boolean endArrow) {
}
