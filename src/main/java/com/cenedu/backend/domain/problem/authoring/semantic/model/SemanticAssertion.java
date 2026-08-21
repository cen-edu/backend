package com.cenedu.backend.domain.problem.authoring.semantic.model;

public record SemanticAssertion(String key, SemanticAssertionType type, String leftKey, String rightKey,
                                String expectedValue) {
}
