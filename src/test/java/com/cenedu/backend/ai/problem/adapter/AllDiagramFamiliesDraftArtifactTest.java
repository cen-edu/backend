package com.cenedu.backend.ai.problem.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.ai.problem.render.ProblemDiagramRenderer;
import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
import com.cenedu.backend.global.common.enums.QuestionType;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 모든 도식 family가 동일한 local draft artifact 계약을 지키는지 검증한다. */
class AllDiagramFamiliesDraftArtifactTest {
    @TempDir Path root;

    @Test
    void everyFamilyProducesReadySvgArtifact() throws Exception {
        var sanitizer = new SafeSvgSanitizer();
        var adapter = new LocalDraftAssetProductionAdapter(root.toString(), sanitizer,
                new ProblemDiagramRenderer(sanitizer));

        for (var spec : specs()) {
            var plan = new GeneratedAssetPlan(spec.assetKey(), AssetRole.FIGURE,
                    AssetProductionMode.STRUCTURED_RENDER, AssetOutputFormat.SVG, "figure",
                    new AssetGenerationSpecification(1, "figure", List.of(), List.of(), Map.of(),
                            values(), spec));
            var artifact = adapter.produce(plan, new AssetProductionContext(7L, 2, QuestionType.SHORT_INPUT));
            assertThat(artifact.status()).isEqualTo(DraftAssetStatus.READY);
            assertThat(artifact.contentType()).isEqualTo("image/svg+xml");
            assertThat(artifact.checksum()).hasSize(64);
            Path file = root.resolve(artifact.draftStorageKey());
            assertThat(file).exists();
            var svg = Files.readString(file);
            assertThat(svg).contains("<svg");
            assertThat(svg).doesNotMatch("(?is).*<(script|foreignObject|iframe|object|embed|image|use)\\b.*")
                    .doesNotMatch("(?is).*\\bon[a-z]+\\s*=.*")
                    .doesNotMatch("(?is).*(href|src)\\s*=\\s*['\"]https?://.*")
                    .doesNotContain("javascript:", "url(");
        }
    }

    private List<DiagramSpecV1> specs() {
        var v = new DiagramViewport(640, 240, 16);
        var style = new DiagramStyle("#000000", "#FFFFFF", "#FF0000", 1, "sans-serif", 12);
        return List.of(
                new NumberLineDiagramSpecV1(1, "NUMBER", DiagramKind.NUMBER_LINE, v, style, "MIN", "MAX", "STEP", List.of(), List.of(), false, false),
                new CoordinateGraphDiagramSpecV1(1, "GRAPH", DiagramKind.COORDINATE_GRAPH, v, style, "XMIN", "XMAX", "YMIN", "YMAX", "XTICK", "YTICK", List.of(), List.of(), List.of(), List.of()),
                new DataTableDiagramSpecV1(1, "TABLE", DiagramKind.DATA_TABLE, v, style, List.of("row"), List.of("col"), List.of(new TableCellSpec(0, 0, "VALUE", null)), Set.of()),
                new PlaneGeometryDiagramSpecV1(1, "PLANE", DiagramKind.PLANE_GEOMETRY, v, style,
                        List.of(new PlanePointSpec("A", "X", "Y", "A")), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()),
                new SolidGeometryDiagramSpecV1(1, "SOLID", DiagramKind.SOLID_GEOMETRY, v, style,
                        SolidGeometryKind.RECTANGULAR_PRISM, "WIDTH", "DEPTH", "HEIGHT", null, null, null, List.of())
        );
    }

    private Map<String, SemanticResolvedValue> values() {
        var values = new HashMap<String, SemanticResolvedValue>();
        for (var key : List.of("MIN", "MAX", "STEP", "XMIN", "XMAX", "YMIN", "YMAX", "XTICK", "YTICK", "VALUE", "X", "Y", "WIDTH", "DEPTH", "HEIGHT")) {
            values.put(key, new SemanticResolvedValue(SemanticValueType.INTEGER, key.equals("MIN") ? "-5" : "5", null));
        }
        return values;
    }
}
