package com.cenedu.backend.domain.problem.authoring.diagram;

public record CoordinateLineSpec(String lineKey, String pointAKey, String pointBKey, boolean startArrow,
                                 boolean endArrow, String labelTemplate) {
}
