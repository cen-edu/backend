package com.cenedu.backend.domain.problem.authoring.edit.semantic;

public class SemanticPatchConflictException extends RuntimeException {
    private final String path;
    private final String expected;
    private final String actual;

    public SemanticPatchConflictException(String path, String expected, String actual) {
        super("semantic patch 충돌: " + path);
        this.path = path;
        this.expected = expected;
        this.actual = actual;
    }

    public String path() {
        return path;
    }

    public String expected() {
        return expected;
    }

    public String actual() {
        return actual;
    }
}
