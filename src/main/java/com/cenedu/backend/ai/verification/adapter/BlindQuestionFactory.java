package com.cenedu.backend.ai.verification.adapter;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAssetReference;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotChoice;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotContentBlock;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegment;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotStep;

import org.springframework.stereotype.Component;

/**
 * S1 스냅샷에서 정답과 저작측 의도를 걷어내 {@link BlindQuestion} 을 만든다.
 *
 * <p><b>화이트리스트로 옮긴다.</b> 제거 방식으로 짜면 S1 에 필드가 추가되는 순간 새어 나가고,
 * 저작측은 필드를 추가했다는 사실을 알리지 않는다. 무엇을 옮길지는
 * {@link BlindFieldPolicy} 가 정하고 {@code BlindQuestionLeakTest} 3층이 그 표의 완전성을 지킨다.
 *
 * <p>{@code null} 목록을 빈 목록으로 바꾼다. S1 계약은 호출자가 빈 목록을 주도록 요구하지만,
 * 검증기는 계약을 지키지 않은 입력도 받는 쪽이다 — 여기서 NPE 로 죽으면 검증 자체가 사라진다.
 */
@Component
public class BlindQuestionFactory {

    /**
     * @throws UnsupportedSnapshotVersionException 스냅샷 스키마 버전을 이 Adapter 가 모를 때
     */
    public BlindQuestion from(QuestionSnapshotV1 snapshot) {
        requireSupportedVersion(snapshot);

        SnapshotMetadata metadata = snapshot.metadata();
        return new BlindQuestion(
                metadata == null ? null : metadata.questionType(),
                metadata == null ? null : metadata.presentation(),
                metadata == null ? null : metadata.difficulty(),
                blocks(snapshot.contentBlocks()),
                assets(snapshot.assets()),
                choices(snapshot.choices()),
                steps(snapshot.steps()),
                answerSlots(snapshot.answerUnits()));
    }

    /**
     * 버전을 확인한다. 검증 진입점도 이 메서드를 호출한다 — ASSET 범위는 Blind 를 만들지 않지만
     * 모르는 버전에서 판정하면 안 되는 것은 같다. 두 곳에 같은 조건을 적지 않기 위해 정적 메서드다.
     *
     * <p>Blind 변환은 필드를 이름으로 하나하나 옮기는 코드다. 모르는 버전에서는
     * <b>옮기지 못한 필드가 있는지조차 알 수 없다.</b> 그 상태로 진행하면 누락된 발문으로
     * 문항을 판정하거나, 최악의 경우 새 정답 필드를 그대로 흘린다. 판정을 시도하지 않고 멈추는 편이 맞다.
     */
    static void requireSupportedVersion(QuestionSnapshotV1 snapshot) {
        if (snapshot == null) {
            throw new UnsupportedSnapshotVersionException("스냅샷이 없습니다.");
        }
        if (snapshot.schemaVersion() != QuestionSnapshotV1.CURRENT_SCHEMA_VERSION) {
            throw new UnsupportedSnapshotVersionException(
                    "지원하지 않는 스냅샷 스키마 버전입니다 — expected=%d, actual=%d"
                            .formatted(QuestionSnapshotV1.CURRENT_SCHEMA_VERSION,
                                    snapshot.schemaVersion()));
        }
    }

    private List<BlindQuestion.BlindContentBlock> blocks(List<SnapshotContentBlock> source) {
        return nullSafe(source).stream()
                .filter(block -> block != null)
                .map(block -> new BlindQuestion.BlindContentBlock(
                        block.blockKey(),
                        block.blockKind(),
                        block.displayOrder(),
                        block.text(),
                        block.assetRef(),
                        block.markup()))
                .toList();
    }

    private List<BlindQuestion.BlindAsset> assets(List<SnapshotAssetReference> source) {
        return nullSafe(source).stream()
                .filter(asset -> asset != null)
                .map(asset -> new BlindQuestion.BlindAsset(asset.assetKey(), asset.altText()))
                .toList();
    }

    private List<BlindQuestion.BlindChoice> choices(List<SnapshotChoice> source) {
        return nullSafe(source).stream()
                .filter(choice -> choice != null)
                .map(choice -> new BlindQuestion.BlindChoice(
                        choice.choiceKey(), choice.displayOrder(), choice.content()))
                .toList();
    }

    private List<BlindQuestion.BlindStep> steps(List<SnapshotStep> source) {
        return nullSafe(source).stream()
                .filter(step -> step != null)
                .map(step -> new BlindQuestion.BlindStep(
                        step.stepKey(),
                        step.displayOrder(),
                        step.label(),
                        segments(step.segments())))
                .toList();
    }

    private List<BlindQuestion.BlindSegment> segments(List<SnapshotSegment> source) {
        return nullSafe(source).stream()
                .filter(segment -> segment != null)
                .map(segment -> new BlindQuestion.BlindSegment(
                        segment.type(), segment.text(), segment.unitKey()))
                .toList();
    }

    /** 답이 들어가는 자리만 남긴다. answerRaw · answerNormalized · diagnosticType 은 옮기지 않는다. */
    private List<BlindQuestion.BlindAnswerSlot> answerSlots(List<SnapshotAnswerUnit> source) {
        return nullSafe(source).stream()
                .filter(unit -> unit != null)
                .map(unit -> new BlindQuestion.BlindAnswerSlot(
                        unit.unitKey(),
                        unit.stepKey(),
                        unit.displayOrder(),
                        unit.compareMethod(),
                        unit.displayUnit()))
                .toList();
    }

    private static <T> List<T> nullSafe(List<T> source) {
        return source == null ? List.of() : source;
    }
}
