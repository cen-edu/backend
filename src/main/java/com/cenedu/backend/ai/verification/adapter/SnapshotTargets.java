package com.cenedu.backend.ai.verification.adapter;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.cenedu.backend.domain.problem.authoring.edit.EditTargetType;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;

/**
 * 수정 대상 위치({@code EditTargetType} + 논리 키)가 가리키는 스냅샷 값을 문자열로 뽑는다.
 *
 * <p>비교를 위한 표현이다. 값이 같은지만 보면 되므로 사람이 읽을 형태를 따로 만들지 않는다.
 * 대신 <b>필드를 빠뜨리면 "안 바뀌었다"로 보인다</b> — 예를 들어 CHOICE 에서 content 만 보고
 * displayOrder 를 빼면 보기 순서만 바꾼 수정이 미반영으로 판정된다. 영역마다 그 영역을 이루는 값을
 * 전부 넣는다.
 *
 * <p>{@code targetKey} 가 {@code null} 이면 그 영역 전체다. 계약 주석이 그렇게 정하고 있다.
 */
final class SnapshotTargets {

    private SnapshotTargets() {
    }

    /** 위치가 가리키는 값. 그 위치가 스냅샷에 없으면 {@code null}. */
    static String valueAt(QuestionSnapshotV1 snapshot, EditTargetType targetType, String targetKey) {
        if (snapshot == null || targetType == null) {
            return null;
        }
        return switch (targetType) {
            case QUESTION_BODY -> textBlocks(snapshot);
            case CONTENT_BLOCK -> contentBlocks(snapshot, targetKey);
            case CHOICE -> choices(snapshot, targetKey);
            case STEP -> steps(snapshot, targetKey);
            case ANSWER_UNIT -> answerUnits(snapshot, targetKey);
            case EXPLANATION -> snapshot.explanation();
            case LEARNING_GUIDE -> Objects.toString(snapshot.learningGuide(), null);
            case RUBRIC_ITEM -> rubricItems(snapshot, targetKey);
            case ASSET -> assets(snapshot, targetKey);
            case QUESTION_TYPE -> snapshot.metadata() == null
                    ? null : Objects.toString(snapshot.metadata().questionType(), null);
            case DIFFICULTY -> snapshot.metadata() == null ? null : snapshot.metadata().difficulty();
            // 문항 전체가 대상이면 스냅샷 전체를 값으로 본다.
            case WHOLE_QUESTION -> snapshot.toString();
        };
    }

    private static String textBlocks(QuestionSnapshotV1 snapshot) {
        return nullSafe(snapshot.contentBlocks()).stream()
                .filter(block -> block != null && block.blockKind() == SnapshotBlockKind.TEXT)
                .map(block -> block.blockKey() + "=" + block.text())
                .collect(Collectors.joining("\n"));
    }

    private static String contentBlocks(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.contentBlocks()).stream()
                .filter(block -> block != null && matches(targetKey, block.blockKey()))
                .map(block -> block.blockKey() + "|" + block.blockKind() + "|" + block.displayOrder()
                        + "|" + block.text() + "|" + block.assetRef() + "|" + block.markup())
                .collect(Collectors.joining("\n"));
    }

    private static String choices(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.choices()).stream()
                .filter(choice -> choice != null && matches(targetKey, choice.choiceKey()))
                .map(choice -> choice.choiceKey() + "|" + choice.displayOrder() + "|" + choice.content())
                .collect(Collectors.joining("\n"));
    }

    private static String steps(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.steps()).stream()
                .filter(step -> step != null && matches(targetKey, step.stepKey()))
                .map(step -> step.stepKey() + "|" + step.displayOrder() + "|" + step.label()
                        + "|" + nullSafe(step.segments()))
                .collect(Collectors.joining("\n"));
    }

    private static String answerUnits(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.answerUnits()).stream()
                .filter(unit -> unit != null && matches(targetKey, unit.unitKey()))
                .map(unit -> unit.unitKey() + "|" + unit.stepKey() + "|" + unit.displayOrder()
                        + "|" + unit.answerRaw() + "|" + unit.answerNormalized()
                        + "|" + unit.compareMethod() + "|" + unit.diagnosticType()
                        + "|" + unit.displayUnit())
                .collect(Collectors.joining("\n"));
    }

    private static String rubricItems(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.rubricItems()).stream()
                .filter(rubric -> rubric != null && matches(targetKey, rubric.rubricKey()))
                .map(rubric -> rubric.rubricKey() + "|" + rubric.displayOrder()
                        + "|" + rubric.criterion() + "|" + rubric.weightPercent())
                .collect(Collectors.joining("\n"));
    }

    private static String assets(QuestionSnapshotV1 snapshot, String targetKey) {
        return nullSafe(snapshot.assets()).stream()
                .filter(asset -> asset != null && matches(targetKey, asset.assetKey()))
                .map(asset -> asset.assetKey() + "|" + asset.altText())
                .collect(Collectors.joining("\n"));
    }

    private static boolean matches(String targetKey, String logicalKey) {
        return targetKey == null || targetKey.equals(logicalKey);
    }

    private static <T> List<T> nullSafe(List<T> source) {
        return source == null ? List.of() : source;
    }
}
