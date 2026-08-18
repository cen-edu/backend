package com.cenedu.backend.domain.problem.authoring.asset;

import java.util.List;
import java.util.Map;

/** 원본 SVG가 아닌 검증 가능한 시각 요구사항과 구조화 렌더링 데이터를 담는다. */
public record AssetGenerationSpecification(
        int schemaVersion,
        String visualDescription,
        List<String> requiredElements,
        List<String> forbiddenElements,
        Map<String, Object> renderData
) {
}
