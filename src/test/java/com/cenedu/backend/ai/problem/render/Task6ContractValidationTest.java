package com.cenedu.backend.ai.problem.render;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.cenedu.backend.ai.problem.adapter.SafeSvgSanitizer;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class Task6ContractValidationTest {
    @Test void sanitizerRejectsForbiddenRootAttributeAndMissingViewBox() {
        var sanitizer = new SafeSvgSanitizer();
        assertThatThrownBy(() -> sanitizer.sanitize("<svg viewBox=\"0 0 10 10\" onclick=\"x\"/>" )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sanitizer.sanitize("<svg/>" )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void tableRendererRejectsOutOfRangeCoordinates() {
        var spec = new DataTableDiagramSpecV1(1,"T",DiagramKind.DATA_TABLE,new DiagramViewport(200,100,8),
                new DiagramStyle("#000","#FFF","#F00",1,"sans",12),List.of("r"),List.of("c"),
                List.of(new TableCellSpec(2,0,"V","x")),Set.of());
        assertThatThrownBy(() -> new DataTableSvgRenderer().render(spec)).isInstanceOf(IllegalArgumentException.class);
    }
}
