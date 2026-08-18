package com.cenedu.backend.ai.verification.adapter;

import java.util.Map;
import java.util.Set;

/**
 * S1 스냅샷의 어느 필드를 Solver 에게 보여 줄지에 대한 판정 표.
 *
 * <p><b>허용 목록과 명시제외 목록의 이중 목록이다.</b> 허용 목록만 두면 새 필드가 조용히 제외되고,
 * 제외 목록만 두면 새 필드가 조용히 새어 나간다. 둘 다 두면 어느 쪽에도 없는 필드는
 * {@code BlindQuestionLeakTest} 3층이 실패로 만든다. <b>조용히 통과하는 것이 가장 나쁜 결과다.</b>
 *
 * <p>키는 {@code 레코드이름.필드이름} 이다. 필드 이름만 쓰면 서로 다른 레코드의 같은 이름
 * ({@code displayOrder}, {@code text}, {@code unitKey})이 한 칸을 나눠 쓰게 되고, 한쪽 판정이
 * 다른 쪽까지 덮는다.
 *
 * <p>순회는 허용된 레코드 필드만 따라 내려간다. 제외한 레코드({@code learningGuide} 등)의 내부는
 * 애초에 옮기지 않으므로 열거할 이유가 없다.
 */
final class BlindFieldPolicy {

    /** Blind 로 옮기는 필드. */
    static final Set<String> ALLOWED = Set.of(
            "QuestionSnapshotV1.metadata",
            "QuestionSnapshotV1.contentBlocks",
            "QuestionSnapshotV1.assets",
            "QuestionSnapshotV1.choices",
            "QuestionSnapshotV1.steps",
            "QuestionSnapshotV1.answerUnits",

            "SnapshotMetadata.questionType",
            "SnapshotMetadata.presentation",
            "SnapshotMetadata.difficulty",

            "SnapshotContentBlock.blockKey",
            "SnapshotContentBlock.blockKind",
            "SnapshotContentBlock.displayOrder",
            "SnapshotContentBlock.text",
            "SnapshotContentBlock.assetRef",
            "SnapshotContentBlock.markup",

            "SnapshotAssetReference.assetKey",
            "SnapshotAssetReference.altText",

            "SnapshotChoice.choiceKey",
            "SnapshotChoice.displayOrder",
            "SnapshotChoice.content",

            "SnapshotStep.stepKey",
            "SnapshotStep.displayOrder",
            "SnapshotStep.label",
            "SnapshotStep.segments",

            "SnapshotSegment.type",
            "SnapshotSegment.text",
            "SnapshotSegment.unitKey",

            "SnapshotAnswerUnit.unitKey",
            "SnapshotAnswerUnit.stepKey",
            "SnapshotAnswerUnit.displayOrder",
            "SnapshotAnswerUnit.compareMethod",
            "SnapshotAnswerUnit.displayUnit");

    /**
     * 옮기지 않는 필드와 그 이유. 이유를 값으로 두는 이유는, 이유가 없으면 다음 사람이
     * "왜 빠졌는지 모르겠으니 넣어 보자"로 시작하기 때문이다.
     *
     * <p>제외 사유는 두 종류다. <b>정답류</b>는 Solver 가 보면 답을 베낀다. <b>저작측 의도</b>는
     * 답은 아니지만 저작측의 사고를 따라가게 만들어 독립성을 깬다. 후자를 가볍게 보면
     * 검증기가 저작측 관점을 그대로 재생산하고, 대조는 통과한다.
     */
    static final Map<String, String> EXCLUDED = Map.ofEntries(
            Map.entry("QuestionSnapshotV1.schemaVersion",
                    "Solver 판단에 무관하다. 버전 확인은 Blind 변환이 아니라 검증 진입점의 책임이다."),
            Map.entry("QuestionSnapshotV1.explanation", "정답류. 해설에 답과 풀이가 그대로 있다."),
            Map.entry("QuestionSnapshotV1.learningGuide",
                    "저작측 의도. 개념 안내를 보면 저작측이 의도한 풀이 경로를 따라간다."),
            Map.entry("QuestionSnapshotV1.rubricItems",
                    "저작측 의도. 채점 기준은 무엇을 써야 만점인지 알려 준다. "
                            + "RUBRIC_QUALITY 검사는 Solver 호출과 분리된 별도 호출로 원본을 본다."),

            Map.entry("SnapshotMetadata.subUnitId",
                    "저작측 의도. 어느 소단원 문제인지 알면 풀이 방법이 좁혀진다."),
            Map.entry("SnapshotMetadata.topicCode", "저작측 의도. subUnitId 와 같은 이유다."),
            Map.entry("SnapshotMetadata.evaluationArea",
                    "저작측 의도. 계산인지 추론인지 알려 주면 접근 방식을 지정하는 것과 같다."),
            Map.entry("SnapshotMetadata.derivedFromQuestionId",
                    "저작측 의도. 원본 문항을 가리키는 값이며 Solver 가 쓸 일이 없다."),

            Map.entry("SnapshotAnswerUnit.answerRaw", "정답류. 답 그 자체다."),
            Map.entry("SnapshotAnswerUnit.answerNormalized", "정답류. answerRaw 의 정규형이다."),
            Map.entry("SnapshotAnswerUnit.diagnosticType",
                    "저작측 의도. 이 칸이 해석·모델링·계산·답 중 무엇을 재는지 알려 주면 "
                            + "무엇을 써야 하는지 알려 주는 것과 같다."));

    private BlindFieldPolicy() {
    }
}
