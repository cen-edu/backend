package com.cenedu.backend.domain.problem.ai.model;

/**
 * 객관식 보기 한 개다.
 *
 * <p>{@code choiceKey}는 보기 순서와 분리된 논리 키다. 보기 순서가 바뀌면
 * {@code displayOrder}만 바뀌고, 정답 answer unit은 기존 {@code choiceKey}를 계속 참조한다.
 */
public record SnapshotChoice(
        String choiceKey,
        int displayOrder,
        String content
) {
}
