package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

public final class SemanticPlaceholderValidator {
    private final SemanticTemplateEngine engine = new SemanticTemplateEngine();

    public String render(String t, Map<String, SemanticResolvedValue> v) {
        return engine.render(t, v);
    }
}
