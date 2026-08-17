package com.cenedu.backend.domain.problem.authoring.model;

/**
 * 풀이 단계 문장을 구성하는 텍스트·입력칸·앞선 답 참조 단위다.
 *
 * <p>{@code TEXT}는 {@code text}만 사용한다. {@code BLANK}와 {@code ANSWER_REF}는
 * {@code unitKey}만 사용하며, ANSWER_REF는 정답이 아니라 학생이 앞선 빈칸에 입력한 현재 값을
 * 읽기 전용으로 표시한다.
 */
public record SnapshotSegment(
        SnapshotSegmentType type,
        String text,
        String unitKey
) {
}
