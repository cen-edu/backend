package com.cenedu.backend.domain.problem.authoring.edit.semantic;

public record SemanticPatchOperation(SemanticPatchOperationType type, String path, String expectedOldValue,
                                     String newValue) {
}
