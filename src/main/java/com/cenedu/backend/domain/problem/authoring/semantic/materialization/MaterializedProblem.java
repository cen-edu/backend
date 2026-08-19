package com.cenedu.backend.domain.problem.authoring.semantic.materialization;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1; import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan; import java.util.List;
public record MaterializedProblem(QuestionSnapshotV1 snapshot,List<GeneratedAssetPlan> assetPlans,SemanticMaterializationReport report){}
