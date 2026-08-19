package com.cenedu.backend.domain.problem.authoring.semantic.model;
public record SemanticParameter(String key,SemanticValueType valueType,String value,String unit,boolean editable,SemanticNumericBounds bounds) {}
