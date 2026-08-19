package com.cenedu.backend.domain.problem.dto.response;

import com.cenedu.backend.domain.problem.authoring.edit.semantic.SemanticValueChange;

/** 교사 preview에 노출하는 answer-free parameter 변경이다. */
public record ProblemParameterChangeResponse(String key, String oldValue, String newValue,
        String oldUnit, String newUnit) {
    public static ProblemParameterChangeResponse from(SemanticValueChange change) {
        return new ProblemParameterChangeResponse(change.key(), change.oldValue(), change.newValue(),
                change.oldUnit(), change.newUnit());
    }
}
