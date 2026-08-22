package com.cenedu.backend.domain.problem.authoring.diagram;

import com.fasterxml.jackson.annotation.JsonAlias;

public record DiagramStyle(@JsonAlias("stroke") String strokeColor,
                           @JsonAlias("fill") String fillColor,
                           @JsonAlias("accent") String accentColor,
                           int strokeWidth, String fontFamily,
                           int fontSize) {
}
