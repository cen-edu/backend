package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.port.ProblemSemanticMaterializer;
import com.cenedu.backend.domain.problem.authoring.semantic.model.*;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.*;
import com.cenedu.backend.domain.problem.authoring.semantic.validation.*;
import com.cenedu.backend.domain.problem.authoring.model.*;

import java.util.*;

import com.cenedu.backend.domain.problem.authoring.diagram.*;

public final class DefaultProblemSemanticMaterializer implements ProblemSemanticMaterializer {
    private final ProblemSemanticModelValidator validator = new ProblemSemanticModelValidator(new SemanticUnitAndBoundsValidator(), new SemanticConstraintValidator(), new SemanticAssertionValidator());
    private final SemanticAnswerExposureValidator exposureValidator = new SemanticAnswerExposureValidator();

    public MaterializedProblem materialize(ProblemSemanticModelV1 m) {
        validator.validate(m);
        var e = new SemanticComputationEngine().evaluate(m);
        validator.validateResolved(m, e.values());
        exposureValidator.validate(m, e.values());
        var snapshot = new SemanticSnapshotFactory().create(m, e.values());
        var descriptions = new SemanticVisualDescriptionFactory().create(m.diagrams(), e.values());
        var plans = new SemanticAssetPlanFactory().create(m.diagrams(), e.values(), descriptions);
        var blocks = new ArrayList<>(snapshot.contentBlocks());
        var refs = new ArrayList<SnapshotAssetReference>();
        var keys = new LinkedHashSet<String>();
        int order = 2;
        for (var d : m.diagrams()) {
            if (d instanceof DataTableDiagramSpecV1 table) {
                blocks.add(new SnapshotContentBlock("CB" + order++, SnapshotBlockKind.TABLE,
                        blocks.size(), null, null,
                        new SemanticTableMarkupFactory().create(table, e.values())));
            }
            blocks.add(new SnapshotContentBlock("CB" + order++, SnapshotBlockKind.FIGURE,
                    blocks.size(), null, d.assetKey(), null));
            refs.add(new SnapshotAssetReference(d.assetKey(), descriptions.get(d.assetKey())));
            keys.add(d.assetKey());
        }
        if (!m.diagrams().isEmpty())
            snapshot = new QuestionSnapshotV1(snapshot.schemaVersion(), snapshot.metadata(), List.copyOf(blocks), List.copyOf(refs), snapshot.choices(), snapshot.steps(), snapshot.answerUnits(), snapshot.explanation(), snapshot.learningGuide(), snapshot.rubricItems());
        var values = new LinkedHashMap<String, String>();
        e.values().forEach((k, v) -> values.put(k, v.canonicalValue()));
        return new MaterializedProblem(snapshot, plans, new SemanticMaterializationReport(1, e.topologicalOrder(), Map.copyOf(values), Set.of(), Set.copyOf(keys)));
    }
}
