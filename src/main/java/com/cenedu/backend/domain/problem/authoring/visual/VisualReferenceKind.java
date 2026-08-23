package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind;

/** 검색과 생성 경계에서 사용하는 시각 자료 유형이다. */
public enum VisualReferenceKind {
    NONE,
    UNKNOWN_FIGURE,
    NUMBER_LINE,
    COORDINATE_GRAPH,
    DATA_TABLE,
    PLANE_GEOMETRY,
    SOLID_GEOMETRY;

    /** 도식 스키마의 종류를 외부 검색·생성 계약의 종류로 변환한다. */
    public static VisualReferenceKind fromDiagramKind(DiagramKind kind) {
        if (kind == null) return NONE;
        return valueOf(kind.name());
    }
}
