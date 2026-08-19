package com.cenedu.backend.ai.problem.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.authoring.edit.*;
import com.cenedu.backend.domain.problem.authoring.model.*;
import org.springframework.stereotype.Component;

/** AI 수정 결과에서 보호 영역을 기준 Snapshot 값으로 강제 복원한다. */
@Component
public class ProblemModificationSnapshotMerger {
    public QuestionSnapshotV1 merge(ProblemEditExecutionPlan plan, QuestionSnapshotV1 base,
                                    QuestionSnapshotV1 candidate) {
        if (plan.action() == EditAction.REPLACE) return candidate;
        boolean bodyEditable = editable(plan, EditTargetType.QUESTION_BODY, null);
        var metadata = new SnapshotMetadata(
                editable(plan, EditTargetType.QUESTION_TYPE, null) ? candidate.metadata().questionType() : base.metadata().questionType(),
                candidate.metadata().presentation(),
                editable(plan, EditTargetType.DIFFICULTY, null) ? candidate.metadata().difficulty() : base.metadata().difficulty(),
                base.metadata().subUnitId(), base.metadata().topicCode(), base.metadata().evaluationArea(),
                base.metadata().derivedFromQuestionId());
        return new QuestionSnapshotV1(candidate.schemaVersion(), metadata,
                bodyEditable ? candidate.contentBlocks() : merge(base.contentBlocks(), candidate.contentBlocks(),
                        SnapshotContentBlock::blockKey, EditTargetType.CONTENT_BLOCK, plan),
                merge(base.assets(), candidate.assets(), SnapshotAssetReference::assetKey, EditTargetType.ASSET, plan),
                merge(base.choices(), candidate.choices(), SnapshotChoice::choiceKey, EditTargetType.CHOICE, plan),
                merge(base.steps(), candidate.steps(), SnapshotStep::stepKey, EditTargetType.STEP, plan),
                merge(base.answerUnits(), candidate.answerUnits(), SnapshotAnswerUnit::unitKey, EditTargetType.ANSWER_UNIT, plan),
                editable(plan, EditTargetType.EXPLANATION, null) ? candidate.explanation() : base.explanation(),
                editable(plan, EditTargetType.LEARNING_GUIDE, null) ? candidate.learningGuide() : base.learningGuide(),
                merge(base.rubricItems(), candidate.rubricItems(), SnapshotRubricItem::rubricKey,
                        EditTargetType.RUBRIC_ITEM, plan));
    }

    private boolean editable(ProblemEditExecutionPlan plan, EditTargetType type, String key) {
        return java.util.stream.Stream.concat(plan.requestedTargets().stream(), plan.dependentTargets().stream())
                .anyMatch(target -> target.targetType() == type
                        && (key == null || java.util.Objects.equals(key, target.targetKey())));
    }

    private <T> List<T> merge(List<T> base, List<T> candidate, Function<T, String> key,
                              EditTargetType type, ProblemEditExecutionPlan plan) {
        var candidateByKey = candidate.stream().collect(Collectors.toMap(key, Function.identity(), (a, b) -> a));
        List<T> result = new ArrayList<>();
        for (T original : base) {
            String logicalKey = key.apply(original);
            result.add(editable(plan, type, logicalKey)
                    ? candidateByKey.getOrDefault(logicalKey, original) : original);
        }
        return List.copyOf(result);
    }
}
