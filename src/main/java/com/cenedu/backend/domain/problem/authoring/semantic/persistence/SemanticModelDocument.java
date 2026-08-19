package com.cenedu.backend.domain.problem.authoring.semantic.persistence;

public record SemanticModelDocument(int schemaVersion, String json, String sha256) { }
