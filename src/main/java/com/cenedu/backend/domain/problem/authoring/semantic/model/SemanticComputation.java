package com.cenedu.backend.domain.problem.authoring.semantic.model;
import java.util.List;
public record SemanticComputation(String key,SemanticOperation operation,List<String> operands,String literal,String unit,String result) { public SemanticComputation { operands=List.copyOf(operands); } }
