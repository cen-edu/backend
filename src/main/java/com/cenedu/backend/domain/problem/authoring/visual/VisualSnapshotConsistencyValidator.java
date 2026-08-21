package com.cenedu.backend.domain.problem.authoring.visual;

import com.cenedu.backend.domain.problem.authoring.asset.GeneratedAssetPlan;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.semantic.model.ProblemSemanticModelV1;

import java.util.HashSet;
import java.util.List;

/** 시각 필수 여부와 Snapshot·계획의 자산 참조를 함께 검증한다. */
public final class VisualSnapshotConsistencyValidator {
    /** 시각 자료가 필요한 semantic 후보의 표시 계약을 검증한다. */
    public void validate(ProblemSemanticModelV1 model, QuestionSnapshotV1 snapshot,
                         List<GeneratedAssetPlan> plans) {
        boolean required = model != null && model.intent() != null && model.intent().visualRequired();
        var actualPlans = plans == null ? List.<GeneratedAssetPlan>of() : plans;
        var assets = snapshot.assets() == null ? List.<com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference>of() : snapshot.assets();
        if (required && (assets.isEmpty() || actualPlans.isEmpty())) {
            throw new IllegalArgumentException("시각 필수 문항에는 자산 계획과 Snapshot 자산이 필요합니다.");
        }
        if (!required && !assets.isEmpty()) {
            throw new IllegalArgumentException("시각 비필수 문항에는 자산을 포함할 수 없습니다.");
        }
        var keys = assets.stream().map(a -> a.assetKey()).toList();
        var planKeys = actualPlans.stream().map(GeneratedAssetPlan::assetKey).toList();
        if (!keys.equals(planKeys) || new HashSet<>(keys).size() != keys.size()) {
            throw new IllegalArgumentException("Snapshot 자산과 계획의 key가 일치하지 않습니다.");
        }
        if (required && snapshot.contentBlocks().stream().noneMatch(block ->
                block.blockKind() == com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind.FIGURE
                        && block.assetRef() != null)) {
            throw new IllegalArgumentException("시각 필수 문항에는 FIGURE assetRef가 필요합니다.");
        }
    }
}
