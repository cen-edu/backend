package com.cenedu.backend.domain.problem.authoring.semantic.evaluation;
import com.cenedu.backend.domain.problem.authoring.semantic.model.SemanticValueType;
public record SemanticResolvedValue(SemanticValueType valueType,String canonicalValue,String unit) {}
