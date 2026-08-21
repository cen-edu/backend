package com.cenedu.backend.domain.problem.authoring.diagram;

public record RenderedDiagram(String assetKey, String svg, String sha256, int widthPx, int heightPx,
                              String rendererVersion) {
}
