package com.cenedu.backend.ai.verification.adapter;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.SnapshotBlockKind;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotSegmentType;
import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * Solver 에게 보여 줄 문항. 정답과 저작측 의도를 뺀 결과다.
 *
 * <p>{@code QuestionSnapshotV1} 을 감싸거나 상속하지 않는다. 감싸면 필드 하나만 새로 들어와도
 * 그대로 흘러가고, 그때 아무도 알아채지 못한다. 여기 있는 필드는 전부 손으로 옮긴 것이다.
 *
 * <p>이 타입은 {@code ai.verification.adapter} 내부 구현이며 계약이 아니다. 도메인이 이 타입을
 * 알 필요가 없고, 알게 되면 Blind 범위를 도메인이 결정하기 시작한다.
 *
 * <p>metadata 를 중첩하지 않고 세 값을 위로 끌어올린 이유: {@code SnapshotMetadata} 와 같은 모양의
 * 타입을 두면 나중에 누군가 통째로 복사하는 지름길을 만든다. 남길 값이 셋뿐이라 평평하게 둔다.
 */
public record BlindQuestion(
        QuestionType questionType,
        QuestionPresentation presentation,
        String difficulty,
        List<BlindContentBlock> contentBlocks,
        List<BlindAsset> assets,
        List<BlindChoice> choices,
        List<BlindStep> steps,
        List<BlindAnswerSlot> answerUnits
) {

    /** 학생 화면에 보이는 발문·그림·표 블록. 화면에 나가는 값이므로 그대로 옮긴다. */
    public record BlindContentBlock(
            String blockKey,
            SnapshotBlockKind blockKind,
            int displayOrder,
            String text,
            String assetRef,
            String markup
    ) {
    }

    /** 그림의 논리 키와 대체 텍스트. altText 는 그림에 보이는 것만 설명하도록 저작측이 제약한다. */
    public record BlindAsset(String assetKey, String altText) {
    }

    /** 객관식 보기. 어느 것이 정답인지는 담지 않는다. */
    public record BlindChoice(String choiceKey, int displayOrder, String content) {
    }

    /** STEP_FILL 의 풀이 단계. */
    public record BlindStep(
            String stepKey, int displayOrder, String label, List<BlindSegment> segments
    ) {
    }

    /** 단계 문장의 조각. BLANK 는 학생이 채울 칸이고 ANSWER_REF 는 앞 칸의 입력값 표시다. */
    public record BlindSegment(SnapshotSegmentType type, String text, String unitKey) {
    }

    /**
     * 답을 적어야 하는 자리. <b>답은 없다.</b>
     *
     * <p>이름을 {@code BlindAnswerUnit} 으로 두지 않았다. 답이 없는 타입에 answerUnit 이라는 이름을
     * 붙이면 나중에 {@code answerRaw} 를 채우고 싶어진다.
     *
     * <p>{@code compareMethod} 는 허용하고 {@code answerRaw} 는 금지하는 이유가 다르다 —
     * 비교 방식은 답의 <b>형태</b>만 알려 준다(집합인지 값인지, 대입 검증인지). Solver 가 답을 어떤
     * 모양으로 쓸지 정하는 데 필요하다. {@code answerRaw} 는 답 그 자체다.
     */
    public record BlindAnswerSlot(
            String unitKey,
            String stepKey,
            int displayOrder,
            CompareMethod compareMethod,
            String displayUnit
    ) {
    }
}
