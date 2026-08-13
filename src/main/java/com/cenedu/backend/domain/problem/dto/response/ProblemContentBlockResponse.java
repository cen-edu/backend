package com.cenedu.backend.domain.problem.dto.response;

public record ProblemContentBlockResponse(
    String blockId,
    String blockKind,
    int displayOrder,
    String text,
    String assetRef,
    String markup
) {
}
