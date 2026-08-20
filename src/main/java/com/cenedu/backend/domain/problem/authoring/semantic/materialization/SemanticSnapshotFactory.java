package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.*;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;

import java.util.*;

public final class SemanticSnapshotFactory {
    public QuestionSnapshotV1 create(ProblemSemanticModelV1 m, Map<String, SemanticResolvedValue> v) {
        var e = m.intent();
        var p = m.presentation();
        var type = e.questionType();
        var choices = p.choices().stream().map(x -> new SnapshotChoice(x.choiceKey(), x.displayOrder(), render(x.contentTemplate(), v))).toList();
        var answers = new ArrayList<SnapshotAnswerUnit>();
        if (type != null && type.name().equals("SHORT_INPUT")) {
            var x = v.get(e.targetKey());
            answers.add(new SnapshotAnswerUnit("MAIN", null, 0, x.canonicalValue(), x.canonicalValue(), com.cenedu.backend.global.common.enums.CompareMethod.VALUE, null, x.unit()));
        }
        var meta = new SnapshotMetadata(type, QuestionPresentation.TEXT_ONLY, e.difficulty(), m.curriculum().subUnitId(), null, e.evaluationArea(), null);
        return new QuestionSnapshotV1(1, meta, List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0, render(p.questionTemplate(), v), null, null)), List.of(), choices, List.of(), answers, render(p.explanationTemplate(), v), null, List.of());
    }

    private String render(String s, Map<String, SemanticResolvedValue> v) {
        return new SemanticTemplateEngine().render(s, v);
    }
}
