package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;

/** 원본 시각 자료의 종류와 구조 요약을 전달하며 정답 값은 포함하지 않는다. */
public record VisualReferenceDescriptor(
        String assetKey,
        VisualReferenceKind kind,
        AssetRole role,
        String altText,
        DiagramSpecV1 diagramSpec
) {
    public VisualReferenceDescriptor {
        if (kind == null) throw new IllegalArgumentException("visual reference kind가 필요합니다.");
        altText = altText == null ? "" : altText;
    }
}
