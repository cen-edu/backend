package com.cenedu.backend.domain.problem.authoring.semantic.model;

import java.util.List;

public record SemanticStepTemplate(String stepKey, int displayOrder, String labelTemplate,
                                   List<SemanticSegmentTemplate> segments) {
    public SemanticStepTemplate {
        segments = List.copyOf(segments);
    }
}
