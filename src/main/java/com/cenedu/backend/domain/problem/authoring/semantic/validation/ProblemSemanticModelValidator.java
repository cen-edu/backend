package com.cenedu.backend.domain.problem.authoring.semantic.validation;

import com.cenedu.backend.domain.problem.authoring.semantic.model.*;

import java.util.*;

public final class ProblemSemanticModelValidator {
    private final SemanticUnitAndBoundsValidator units;
    private final SemanticConstraintValidator constraints;
    private final SemanticAssertionValidator assertions;

    public ProblemSemanticModelValidator(SemanticUnitAndBoundsValidator u, SemanticConstraintValidator c, SemanticAssertionValidator a) {
        units = u;
        constraints = c;
        assertions = a;
    }

    public void validate(ProblemSemanticModelV1 m) {
        var v = violations(m);
        if (!v.isEmpty()) throw new SemanticValidationException(v);
    }

    public List<String> violations(ProblemSemanticModelV1 m) {
        var v = new ArrayList<String>();
        if (m.schemaVersion() != 1) v.add("schemaVersion: 1 이어야 합니다.");
        var c = m.curriculum();
        if (c != null) {
            if (!"2022_REVISED".equals(c.curriculumRevision()))
                v.add("curriculum.curriculumRevision: 2022_REVISED 이어야 합니다.");
            if (!"MIDDLE".equals(c.schoolLevel())) v.add("curriculum.schoolLevel: MIDDLE 이어야 합니다.");
            if (c.grade() != 1) v.add("curriculum.grade: 1 이어야 합니다.");
            if (c.subUnitId() == null || c.subUnitId() <= 0) v.add("curriculum.subUnitId: 양수여야 합니다.");
        }
        var keys = new HashSet<String>();
        for (int i = 0; i < m.parameters().size(); i++) {
            var k = m.parameters().get(i).key();
            if (!keys.add(k)) v.add("parameters[" + i + "]: " + k + " 키가 중복되었습니다.");
        }
        for (int i = 0; i < m.computations().size(); i++) {
            var x = m.computations().get(i);
            if (!keys.add(x.key())) v.add("computations[" + i + "].key: " + x.key() + " 키가 중복되었습니다.");
            for (int j = 0; j < x.operands().size(); j++)
                if (!keys.contains(x.operands().get(j)))
                    v.add("computations[" + i + "].operands[" + j + "]: " + x.operands().get(j) + " 키가 존재하지 않습니다.");
        }
        if (m.intent() != null && (m.intent().targetKey() == null || !keys.contains(m.intent().targetKey())))
            v.add("intent.targetKey: 대상 키가 존재하지 않습니다.");
        units.appendDefinitionViolations(m, v);
        constraints.appendDefinitionViolations(m, v);
        assertions.appendDefinitionViolations(m, v);
        return List.copyOf(v);
    }
}
