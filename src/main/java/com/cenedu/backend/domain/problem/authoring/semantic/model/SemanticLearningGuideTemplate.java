package com.cenedu.backend.domain.problem.authoring.semantic.model;
import java.util.List;
public record SemanticLearningGuideTemplate(String conceptTitleTemplate,String summaryTemplate,List<String> keyPointTemplates) { public SemanticLearningGuideTemplate { keyPointTemplates=List.copyOf(keyPointTemplates); } }
