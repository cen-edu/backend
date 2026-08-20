package com.cenedu.backend.domain.problem.authoring.diagram;

import com.fasterxml.jackson.annotation.*;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind", visible = true)
@JsonSubTypes({@JsonSubTypes.Type(value = NumberLineDiagramSpecV1.class, name = "NUMBER_LINE"),
    @JsonSubTypes.Type(value = CoordinateGraphDiagramSpecV1.class, name = "COORDINATE_GRAPH"),
    @JsonSubTypes.Type(value = PlaneGeometryDiagramSpecV1.class, name = "PLANE_GEOMETRY"),
    @JsonSubTypes.Type(value = SolidGeometryDiagramSpecV1.class, name = "SOLID_GEOMETRY"),
    @JsonSubTypes.Type(value = DataTableDiagramSpecV1.class, name = "DATA_TABLE")})
public sealed interface DiagramSpecV1 permits NumberLineDiagramSpecV1, CoordinateGraphDiagramSpecV1, PlaneGeometryDiagramSpecV1, SolidGeometryDiagramSpecV1, DataTableDiagramSpecV1 {
    int CURRENT_SCHEMA_VERSION = 1;

    int schemaVersion();

    String assetKey();

    DiagramKind kind();

    DiagramViewport viewport();

    DiagramStyle style();
}
