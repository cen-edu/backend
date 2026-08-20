package com.cenedu.backend.domain.problem.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotLearningGuide;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotRubricItem;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotStep;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairDelta;
import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairPlan;
import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Repair Delta를 허용된 필드에만 적용하고 나머지 Snapshot은 그대로 보존한다. */
@Component
public class ProblemRepairDeltaMerger {
    private final ObjectMapper objectMapper;

    public ProblemRepairDeltaMerger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 계획과 응답 대상이 정확히 일치하는지 확인한 뒤 새 Snapshot을 만든다. */
    public QuestionSnapshotV1 merge(QuestionSnapshotV1 base, ProblemRepairPlan plan,
                                   ProblemRepairDelta delta) {
        if (base == null || plan == null || !plan.repairable() || delta == null) {
            throw new IllegalArgumentException("Repair 병합에 필요한 값이 없습니다.");
        }
        if (!plan.targets().equals(delta.replacements().keySet())) {
            throw new IllegalArgumentException("Repair 응답 대상이 계획과 일치하지 않습니다.");
        }
        return new QuestionSnapshotV1(
                base.schemaVersion(), base.metadata(),
                list(delta, RepairTarget.CONTENT, base.contentBlocks(), SnapshotContentBlock.class),
                list(delta, RepairTarget.ASSET, base.assets(), SnapshotAssetReference.class),
                list(delta, RepairTarget.CHOICES, base.choices(), SnapshotChoice.class),
                list(delta, RepairTarget.STEPS, base.steps(), SnapshotStep.class),
                list(delta, RepairTarget.ANSWERS, base.answerUnits(), SnapshotAnswerUnit.class),
                text(delta, RepairTarget.EXPLANATION, base.explanation()),
                value(delta, RepairTarget.LEARNING_GUIDE, base.learningGuide(), SnapshotLearningGuide.class),
                list(delta, RepairTarget.RUBRIC, base.rubricItems(), SnapshotRubricItem.class));
    }

    private <T> List<T> list(ProblemRepairDelta delta, RepairTarget target,
                             List<T> original, Class<T> elementType) {
        JsonNode value = node(delta, target);
        if (value == null) return original;
        return objectMapper.convertValue(value,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private <T> T value(ProblemRepairDelta delta, RepairTarget target, T original, Class<T> type) {
        JsonNode value = node(delta, target);
        return value == null ? original : objectMapper.convertValue(value, type);
    }

    private String text(ProblemRepairDelta delta, RepairTarget target, String original) {
        JsonNode value = node(delta, target);
        return value == null ? original : value.asString();
    }

    private JsonNode node(ProblemRepairDelta delta, RepairTarget target) {
        Object value = delta.replacements().get(target);
        return value instanceof JsonNode node ? node : null;
    }
}
