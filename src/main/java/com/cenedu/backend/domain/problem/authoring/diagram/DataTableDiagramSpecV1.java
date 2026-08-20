package com.cenedu.backend.domain.problem.authoring.diagram;

import java.util.*;

public record DataTableDiagramSpecV1(int schemaVersion, String assetKey, DiagramKind kind, DiagramViewport viewport,
                                     DiagramStyle style, List<String> rowHeaderTemplates,
                                     List<String> columnHeaderTemplates, List<TableCellSpec> cells,
                                     Set<TableCellAddress> highlightedCells) implements DiagramSpecV1 {
    public DataTableDiagramSpecV1 {
        rowHeaderTemplates = List.copyOf(rowHeaderTemplates);
        columnHeaderTemplates = List.copyOf(columnHeaderTemplates);
        cells = List.copyOf(cells);
        highlightedCells = Set.copyOf(highlightedCells);
    }
}
