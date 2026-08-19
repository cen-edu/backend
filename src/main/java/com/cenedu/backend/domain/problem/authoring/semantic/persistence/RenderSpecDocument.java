package com.cenedu.backend.domain.problem.authoring.semantic.persistence;

public record RenderSpecDocument(int schemaVersion, String json, String sha256, String rendererVersion) { }
