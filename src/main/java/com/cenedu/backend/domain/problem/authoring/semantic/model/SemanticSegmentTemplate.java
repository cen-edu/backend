package com.cenedu.backend.domain.problem.authoring.semantic.model;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
public record SemanticSegmentTemplate(SemanticSegmentType type,String textTemplate,String unitKey,String valueKey,CompareMethod compareMethod,DiagnosticType diagnosticType,String displayUnitTemplate) {}
