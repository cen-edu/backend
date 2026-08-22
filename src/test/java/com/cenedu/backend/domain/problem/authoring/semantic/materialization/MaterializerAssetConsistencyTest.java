package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.global.common.enums.QuestionType;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class MaterializerAssetConsistencyTest {
    @Test void materializerSharesResolvedValueAcrossSnapshotAndAssetPlan() {
        var model = model();
        var materialized = new DefaultProblemSemanticMaterializer().materialize(model);
        assertThat(new SnapshotStructuralValidator().violations(materialized.snapshot())).isEmpty();
        assertThat(materialized.snapshot().metadata().presentation()).isEqualTo(com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation.WITH_TABLE);
        assertThat(materialized.snapshot().contentBlocks()).anyMatch(block -> "F1".equals(block.assetRef()));
        assertThat(materialized.snapshot().contentBlocks()).anyMatch(block -> block.blockKind() == SnapshotBlockKind.TABLE
                && block.markup().contains("<table") && block.markup().contains("7"));
        assertThat(materialized.snapshot().assets()).singleElement()
                .satisfies(asset -> assertThat(asset.altText()).contains("행과 열").contains("7"));
        assertThat(materialized.assetPlans()).singleElement().satisfies(plan -> {
            assertThat(plan.role()).isEqualTo(com.cenedu.backend.domain.problem.entity.enums.AssetRole.TABLE);
            assertThat(plan.assetKey()).isEqualTo("F1");
            assertThat(plan.specification().resolvedValues().get("VALUE").canonicalValue()).isEqualTo("7");
        });
    }

    private ProblemSemanticModelV1 model() {
        var intent = new SemanticProblemIntent(QuestionType.SHORT_INPUT, "low", null, "read table", "VALUE", 1, true);
        var presentation = new SemanticPresentationPlan("표에서 값을 구하시오", List.of(), List.of(), "정답은 {{VALUE}}", null, List.of());
        var table = new DataTableDiagramSpecV1(1, "F1", DiagramKind.DATA_TABLE,
                new DiagramViewport(400, 240, 16), new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12),
                List.of("행"), List.of("값"), List.of(new TableCellSpec(0, 0, "VALUE", null)), Set.of());
        return new ProblemSemanticModelV1(1, new CurriculumScope("2022_REVISED", "MIDDLE", 1, 1, null, 1L, "대", "중", "소"),
                intent, List.of(new SemanticParameter("VALUE", SemanticValueType.INTEGER, "7", null, false, null)),
                List.of(), List.of(), presentation, List.of(table), List.of());
    }
}
