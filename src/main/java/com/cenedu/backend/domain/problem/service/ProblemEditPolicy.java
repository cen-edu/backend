package com.cenedu.backend.domain.problem.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.edit.ConfirmedProblemEditCommand;
import com.cenedu.backend.domain.problem.authoring.edit.EditAction;
import com.cenedu.backend.domain.problem.authoring.edit.EditChangeNature;
import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditExecutionPlan;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditInstruction;
import com.cenedu.backend.domain.problem.authoring.edit.ProblemEditTargetRef;
import com.cenedu.backend.domain.problem.authoring.edit.ReplacementSourcePolicy;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.authoring.edit.semantic.*;
import com.cenedu.backend.global.common.BusinessException;
import com.cenedu.backend.global.common.ErrorCode;
import org.springframework.stereotype.Component;

/** 확정 수정 명령을 자연어 재해석 없이 RESTORE·MODIFY·REPLACE와 허용 범위로 변환한다. */
@Component
public class ProblemEditPolicy {

    /** S1 키를 기준으로 요청·의존·보호 대상을 계산한다. */
    public ProblemEditExecutionPlan plan(ConfirmedProblemEditCommand command,
                                         QuestionSnapshotV1 baseSnapshot,
                                         Long resolvedRestoreVersionId) {
        validate(command, baseSnapshot, resolvedRestoreVersionId);
        if (command.semanticPatch() != null) {
            ProblemSemanticPatch patch = command.semanticPatch();
            if (!java.util.Objects.equals(command.baseVersionId(), patch.baseVersionId()))
                throw new BusinessException(ErrorCode.PROBLEM_EDIT_COMMAND_STALE);
            if (new ProblemSemanticPatchClassifier().classify(patch) != patch.mode())
                throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_EDIT_REJECTED);
            if (patch.mode() == SemanticEditMode.REJECTED)
                throw new BusinessException(ErrorCode.PROBLEM_SEMANTIC_EDIT_REJECTED);
        }
        EditAction action = action(command);
        if (action == EditAction.RESTORE) {
            return new ProblemEditExecutionPlan(
                    command.requestId(), command.sessionId(), command.baseVersionId(),
                    action, ReplacementSourcePolicy.NONE, resolvedRestoreVersionId,
                    List.of(), List.of(), List.of(), List.of(), null);
        }
        if (action == EditAction.REPLACE) {
            return new ProblemEditExecutionPlan(
                    command.requestId(), command.sessionId(), command.baseVersionId(),
                    action, command.replacementSourcePolicy(), null,
                safeInstructions(command.instructions()),
                    command.semanticPatch(),
                    List.of(new ProblemEditTargetRef(EditTargetType.WHOLE_QUESTION, null)),
                    List.of(), List.of(), command.requestedSpecification());
        }

        LinkedHashSet<ProblemEditTargetRef> requested = requestedTargets(command);
        LinkedHashSet<ProblemEditTargetRef> dependent = dependentTargets(
                command, baseSnapshot, requested);
        LinkedHashSet<ProblemEditTargetRef> protectedTargets = allTargets(baseSnapshot);
        protectedTargets.removeAll(requested);
        protectedTargets.removeAll(dependent);
        return new ProblemEditExecutionPlan(
                command.requestId(), command.sessionId(), command.baseVersionId(),
                action, ReplacementSourcePolicy.NONE, null,
                safeInstructions(command.instructions()),
                command.semanticPatch(),
                List.copyOf(requested), List.copyOf(dependent),
                List.copyOf(protectedTargets), command.requestedSpecification());
    }

    private EditAction action(ConfirmedProblemEditCommand command) {
        if (command.restoreReference() != null) {
            return EditAction.RESTORE;
        }
        if (command.semanticPatch() != null) {
            return command.semanticPatch().mode() == SemanticEditMode.STRUCTURAL_REGENERATION
                    ? EditAction.REPLACE : EditAction.MODIFY;
        }
        boolean wholeReplacement = command.requestedSpecification() != null
                || command.replacementSourcePolicy() != ReplacementSourcePolicy.NONE
                || safeInstructions(command.instructions()).stream()
                .anyMatch(instruction -> instruction.targetType() == EditTargetType.WHOLE_QUESTION
                        || instruction.targetType() == EditTargetType.QUESTION_TYPE);
        return wholeReplacement ? EditAction.REPLACE : EditAction.MODIFY;
    }

    private LinkedHashSet<ProblemEditTargetRef> requestedTargets(
            ConfirmedProblemEditCommand command) {
        LinkedHashSet<ProblemEditTargetRef> targets = new LinkedHashSet<>();
        for (ProblemEditInstruction instruction : safeInstructions(command.instructions())) {
            targets.add(new ProblemEditTargetRef(
                    instruction.targetType(), instruction.targetKey()));
        }
        return targets;
    }

    private LinkedHashSet<ProblemEditTargetRef> dependentTargets(
            ConfirmedProblemEditCommand command,
            QuestionSnapshotV1 snapshot,
            Set<ProblemEditTargetRef> requested
    ) {
        LinkedHashSet<ProblemEditTargetRef> targets = new LinkedHashSet<>();
        for (ProblemEditInstruction instruction : safeInstructions(command.instructions())) {
            // QUESTION_BODY는 독립 저장 필드가 아니라 TEXT contentBlocks의 합성 뷰다.
            // 둘 중 하나를 수정하면 다른 표현도 같이 변하므로 보호 대상에서 제외한다.
            if (instruction.targetType() == EditTargetType.QUESTION_BODY) {
                snapshot.contentBlocks().forEach(block -> targets.add(
                        keyed(EditTargetType.CONTENT_BLOCK, block.blockKey())));
            } else if (instruction.targetType() == EditTargetType.CONTENT_BLOCK) {
                targets.add(single(EditTargetType.QUESTION_BODY));
            }
            if (instruction.changeNature() == EditChangeNature.PRESENTATIONAL) {
                continue;
            }
            switch (instruction.targetType()) {
                case QUESTION_BODY, CONTENT_BLOCK -> {
                    addAllAnswerUnits(snapshot, targets);
                    targets.add(single(EditTargetType.EXPLANATION));
                    targets.add(single(EditTargetType.LEARNING_GUIDE));
                }
                case CHOICE -> {
                    addAllAnswerUnits(snapshot, targets);
                    targets.add(single(EditTargetType.EXPLANATION));
                }
                case STEP -> {
                    snapshot.steps().stream()
                            .filter(step -> step.stepKey().equals(instruction.targetKey()))
                            .flatMap(step -> step.segments().stream())
                            .filter(segment -> segment.type() == SnapshotSegmentType.BLANK)
                            .map(segment -> new ProblemEditTargetRef(
                                    EditTargetType.ANSWER_UNIT, segment.unitKey()))
                            .forEach(targets::add);
                    targets.add(single(EditTargetType.EXPLANATION));
                }
                case ANSWER_UNIT -> targets.add(single(EditTargetType.EXPLANATION));
                case ASSET -> {
                    snapshot.contentBlocks().stream()
                            .filter(block -> instruction.targetKey().equals(block.assetRef()))
                            .map(block -> new ProblemEditTargetRef(
                                    EditTargetType.CONTENT_BLOCK, block.blockKey()))
                            .forEach(targets::add);
                    addAllAnswerUnits(snapshot, targets);
                    targets.add(single(EditTargetType.EXPLANATION));
                }
                default -> {
                    // 해설·학습 안내·루브릭 등은 자체 수정만으로 완결된다.
                }
            }
        }
        targets.removeAll(requested);
        return targets;
    }

    private LinkedHashSet<ProblemEditTargetRef> allTargets(QuestionSnapshotV1 snapshot) {
        LinkedHashSet<ProblemEditTargetRef> targets = new LinkedHashSet<>();
        targets.add(single(EditTargetType.QUESTION_BODY));
        snapshot.contentBlocks().forEach(block -> targets.add(
                keyed(EditTargetType.CONTENT_BLOCK, block.blockKey())));
        snapshot.choices().forEach(choice -> targets.add(
                keyed(EditTargetType.CHOICE, choice.choiceKey())));
        snapshot.steps().forEach(step -> targets.add(
                keyed(EditTargetType.STEP, step.stepKey())));
        snapshot.answerUnits().forEach(unit -> targets.add(
                keyed(EditTargetType.ANSWER_UNIT, unit.unitKey())));
        targets.add(single(EditTargetType.EXPLANATION));
        targets.add(single(EditTargetType.LEARNING_GUIDE));
        snapshot.rubricItems().forEach(rubric -> targets.add(
                keyed(EditTargetType.RUBRIC_ITEM, rubric.rubricKey())));
        snapshot.assets().forEach(asset -> targets.add(
                keyed(EditTargetType.ASSET, asset.assetKey())));
        targets.add(single(EditTargetType.DIFFICULTY));
        return targets;
    }

    private void validate(ConfirmedProblemEditCommand command,
                          QuestionSnapshotV1 baseSnapshot,
                          Long resolvedRestoreVersionId) {
        if (command == null || command.requestId() == null
                || command.confirmationMessageId() == null
                || command.sessionId() == null || command.baseVersionId() == null
                || command.replacementSourcePolicy() == null || baseSnapshot == null) {
            throw new IllegalArgumentException("확정 수정 명령 필수값이 누락되었습니다.");
        }
        if (command.restoreReference() != null) {
            if (resolvedRestoreVersionId == null
                    || command.replacementSourcePolicy() != ReplacementSourcePolicy.NONE) {
                throw new IllegalArgumentException("복원은 PASSED Version과 NONE 교체 정책이 필요합니다.");
            }
            return;
        }
        if (command.semanticPatch() == null && safeInstructions(command.instructions()).isEmpty()
                && command.requestedSpecification() == null) {
            throw new IllegalArgumentException("수행할 수정 요청이 없습니다.");
        }
        if (command.requestedSpecification() != null
                && command.replacementSourcePolicy() == ReplacementSourcePolicy.NONE) {
            throw new IllegalArgumentException("문제 유형·난이도 교체는 소스 정책이 필요합니다.");
        }
        Set<ProblemEditTargetRef> existingTargets = allTargets(baseSnapshot);
        for (ProblemEditInstruction instruction : safeInstructions(command.instructions())) {
            ProblemEditTargetRef target = new ProblemEditTargetRef(
                    instruction.targetType(), instruction.targetKey());
            boolean wholeTarget = instruction.targetType() == EditTargetType.WHOLE_QUESTION
                    || instruction.targetType() == EditTargetType.QUESTION_TYPE;
            if (!wholeTarget && !existingTargets.contains(target)) {
                throw new IllegalArgumentException("현재 S1에 없는 수정 대상입니다: " + target);
            }
        }
    }

    private void addAllAnswerUnits(QuestionSnapshotV1 snapshot,
                                   Set<ProblemEditTargetRef> targets) {
        snapshot.answerUnits().forEach(unit -> targets.add(
                keyed(EditTargetType.ANSWER_UNIT, unit.unitKey())));
    }

    private List<ProblemEditInstruction> safeInstructions(
            List<ProblemEditInstruction> instructions) {
        return instructions == null ? List.of() : List.copyOf(instructions);
    }

    private ProblemEditTargetRef single(EditTargetType type) {
        return new ProblemEditTargetRef(type, null);
    }

    private ProblemEditTargetRef keyed(EditTargetType type, String key) {
        return new ProblemEditTargetRef(type, key);
    }
}
