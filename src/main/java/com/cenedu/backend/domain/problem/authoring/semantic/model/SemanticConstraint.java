package com.cenedu.backend.domain.problem.authoring.semantic.model;

import java.util.List;

public record SemanticConstraint(String key, SemanticConstraintType type, List<String> operands, String expectedValue,
                                 String message) {
    public SemanticConstraint {
        operands = List.copyOf(operands);
    }
}
