package com.cenedu.backend.ai.problem.render;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class DataTableResolvedValueTest {
    @Test void resolvedTableValueIsRendered() {
        var table = new DataTableDiagramSpecV1(1, "T1", DiagramKind.DATA_TABLE,
                new DiagramViewport(400, 240, 16), new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                List.of("행"), List.of("값"), List.of(new TableCellSpec(0, 0, "VALUE", null)), Set.of());
        var svg = new DataTableSvgRenderer().render(table,
                Map.of("VALUE", new SemanticResolvedValue(SemanticValueType.INTEGER, "7", null)));
        assertThat(svg).contains("7");
    }
}
