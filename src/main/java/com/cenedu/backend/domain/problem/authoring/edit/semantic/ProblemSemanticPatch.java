package com.cenedu.backend.domain.problem.authoring.edit.semantic;
import java.util.List; import java.util.UUID;
public record ProblemSemanticPatch(int schemaVersion, UUID requestId, Long baseVersionId,
        SemanticEditMode mode, List<SemanticPatchOperation> operations, String assistantMessage) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public ProblemSemanticPatch { operations = operations == null ? List.of() : List.copyOf(operations); }
}
