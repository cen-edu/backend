package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticMaterializationException;

import java.util.*;

public final class SemanticAssertionValidator {
    public void appendDefinitionViolations(ProblemSemanticModelV1 m, List<String> v) {
        if (m == null || m.intent() == null || m.presentation() == null) return;
        if (m.intent().questionType() == com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE) {
            var seen = new HashSet<String>();
            for (var choice : m.presentation().choices()) {
                if (choice.valueKey() == null || choice.valueKey().isBlank()) {
                    v.add("presentation.choices.valueKey: 객관식 choice의 valueKey가 필요합니다.");
                } else if (!seen.add(choice.valueKey())) {
                    v.add("presentation.choices.valueKey: valueKey가 중복되었습니다.");
                }
            }
        }
    }

    public void validateResolved(ProblemSemanticModelV1 m, Map<String, SemanticResolvedValue> values) {
        if (m == null || m.intent() == null || m.presentation() == null) {
            throw new SemanticMaterializationException("semantic assertion 입력이 없습니다.");
        }
        if (m.intent().targetKey() == null || !values.containsKey(m.intent().targetKey())) {
            throw new SemanticMaterializationException("intent.targetKey resolved value가 없습니다.");
        }
        if (m.intent().questionType() == com.cenedu.backend.global.common.enums.QuestionType.MULTIPLE_CHOICE) {
            for (var choice : m.presentation().choices()) {
                if (choice.valueKey() == null || !values.containsKey(choice.valueKey())) {
                    throw new SemanticMaterializationException("choice valueKey resolved value가 없습니다.");
                }
            }
        }
    }
}
