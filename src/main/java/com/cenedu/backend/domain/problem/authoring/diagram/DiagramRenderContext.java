package com.cenedu.backend.domain.problem.authoring.diagram;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public record DiagramRenderContext(Map<String, SemanticResolvedValue> values) {
    public DiagramRenderContext {
        values = Map.copyOf(values);
    }
}
