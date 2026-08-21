package com.cenedu.backend.domain.problem.authoring.asset;

import java.util.List;
import java.util.Map;
import com.cenedu.backend.domain.problem.authoring.diagram.DiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

/** 원본 SVG가 아닌 검증 가능한 시각 요구사항과 구조화 렌더링 데이터를 담는다. */
public record AssetGenerationSpecification(
        int schemaVersion,
        String visualDescription,
        List<String> requiredElements,
        List<String> forbiddenElements,
        Map<String, Object> renderData,
        Map<String, SemanticResolvedValue> resolvedValues,
        DiagramSpecV1 diagramSpec
) {
    public AssetGenerationSpecification {
        renderData = renderData == null ? Map.of() : Map.copyOf(renderData);
        resolvedValues = resolvedValues == null ? Map.of() : Map.copyOf(resolvedValues);
    }

    public AssetGenerationSpecification(int schemaVersion, String visualDescription,
            List<String> requiredElements, List<String> forbiddenElements,
            Map<String, Object> renderData, DiagramSpecV1 diagramSpec) {
        this(schemaVersion, visualDescription, requiredElements, forbiddenElements,
                renderData, Map.of(), diagramSpec);
    }

    public AssetGenerationSpecification(int schemaVersion, String visualDescription,
            List<String> requiredElements, List<String> forbiddenElements,
            Map<String, Object> renderData) {
        this(schemaVersion, visualDescription, requiredElements, forbiddenElements,
                renderData, Map.of(), null);
    }
}
