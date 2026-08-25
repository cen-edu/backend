package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.asset.*;
import com.cenedu.backend.domain.problem.authoring.diagram.*;
import com.cenedu.backend.domain.problem.entity.enums.AssetRole;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class SemanticAssetPlanFactory {
    /** 기존 호출부와의 호환성을 위해 평가값이 없는 계획을 만든다. */
    public List<GeneratedAssetPlan> create(List<DiagramSpecV1> specs) {
        return create(specs, Map.of());
    }

    /** semantic 도식과 동일한 평가값을 각 자산 계획에 보관한다. */
    public List<GeneratedAssetPlan> create(List<DiagramSpecV1> specs,
            Map<String, SemanticResolvedValue> resolvedValues) {
        return create(specs, resolvedValues,
                new SemanticVisualDescriptionFactory().create(specs, resolvedValues));
    }

    /** semantic 도식의 접근성 설명을 자산 계획과 함께 보존한다. */
    public List<GeneratedAssetPlan> create(List<DiagramSpecV1> specs,
            Map<String, SemanticResolvedValue> resolvedValues,
            Map<String, String> descriptions) {
        var out = new ArrayList<GeneratedAssetPlan>();
        for (var s : specs) {
            var elements = new ArrayList<String>();
            elements.add(s.kind().name());
            String description = descriptions.getOrDefault(s.assetKey(), "문항에 포함된 " + s.kind().name() + " 도식");
            out.add(new GeneratedAssetPlan(s.assetKey(), s.kind() == DiagramKind.DATA_TABLE ? AssetRole.TABLE : AssetRole.FIGURE,
                    AssetProductionMode.STRUCTURED_RENDER, AssetOutputFormat.SVG, description,
                    new AssetGenerationSpecification(1, "deterministic " + s.kind().name(), elements,
                            List.of("script", "http", "javascript"), Map.of(), resolvedValues, s)));
        }
        return List.copyOf(out);
    }
}
