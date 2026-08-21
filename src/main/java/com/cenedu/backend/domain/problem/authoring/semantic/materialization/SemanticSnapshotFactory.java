package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.*;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

import java.util.*;

public final class SemanticSnapshotFactory {
    public QuestionSnapshotV1 create(ProblemSemanticModelV1 m, Map<String, SemanticResolvedValue> v) {
        var e = m.intent();
        var p = m.presentation();
        var type = e.questionType();
        var choices = p.choices().stream().map(x -> new SnapshotChoice(x.choiceKey(), x.displayOrder(), render(x.contentTemplate(), v))).toList();
        var answers = new ArrayList<SnapshotAnswerUnit>();
        if (type == QuestionType.MULTIPLE_CHOICE) {
            var target = requireValue(v, e.targetKey(), "객관식 target");
            var matches = p.choices().stream()
                    .filter(choice -> target.canonicalValue().equals(requireValue(v, choice.valueKey(), "choice " + choice.choiceKey()).canonicalValue()))
                    .toList();
            if (matches.size() != 1) {
                throw new SemanticMaterializationException("객관식 target과 일치하는 choice는 정확히 1개여야 합니다.");
            }
            var matched = matches.get(0);
            answers.add(new SnapshotAnswerUnit("MAIN", null, 0, matched.choiceKey(), matched.choiceKey(),
                    CompareMethod.CHOICE, null, null));
        } else if (type == QuestionType.SHORT_INPUT) {
            var x = v.get(e.targetKey());
            var resolved = requireValue(v, e.targetKey(), "short input target");
            answers.add(new SnapshotAnswerUnit("MAIN", null, 0, resolved.canonicalValue(), resolved.canonicalValue(), CompareMethod.VALUE, null, resolved.unit()));
        }
        QuestionPresentation presentation = m.diagrams().isEmpty()
                ? QuestionPresentation.TEXT_ONLY
                : m.diagrams().stream().anyMatch(d -> d.kind() == com.cenedu.backend.domain.problem.authoring.diagram.DiagramKind.DATA_TABLE)
                ? QuestionPresentation.WITH_TABLE : QuestionPresentation.WITH_FIGURE;
        var meta = new SnapshotMetadata(type, presentation, e.difficulty(), m.curriculum().subUnitId(), null, e.evaluationArea(), null);
        return new QuestionSnapshotV1(1, meta, List.of(new SnapshotContentBlock("CB1", SnapshotBlockKind.TEXT, 0, render(p.questionTemplate(), v), null, null)), List.of(), choices, List.of(), answers, render(p.explanationTemplate(), v), null, List.of());
    }

    private SemanticResolvedValue requireValue(Map<String, SemanticResolvedValue> values, String key, String label) {
        SemanticResolvedValue value = key == null ? null : values.get(key);
        if (value == null) throw new SemanticMaterializationException(label + " resolved value가 없습니다.");
        return value;
    }

    private String render(String s, Map<String, SemanticResolvedValue> v) {
        return new SemanticTemplateEngine().render(s, v);
    }
}
