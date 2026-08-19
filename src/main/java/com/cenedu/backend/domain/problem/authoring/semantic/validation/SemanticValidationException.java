package com.cenedu.backend.domain.problem.authoring.semantic.validation;
import java.util.List;
public final class SemanticValidationException extends IllegalArgumentException { private final List<String> violations; public SemanticValidationException(List<String> violations){super(String.join("; ",List.copyOf(violations)));this.violations=List.copyOf(violations);} public List<String> violations(){return violations;} }
