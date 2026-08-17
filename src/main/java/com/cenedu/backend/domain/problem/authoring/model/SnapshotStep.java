package com.cenedu.backend.domain.problem.authoring.model;

import java.util.List;

/**
 * STEP_FILL 문항의 풀이 단계 한 개다.
 *
 * <p>{@code stepKey}는 단계 순서와 분리된 논리 키이며, {@code label}은 학생 화면에 표시할 단계
 * 제목이다. 세그먼트의 목록 순서가 단계 안의 실제 렌더링 순서다.
 */
public record SnapshotStep(
        String stepKey,
        int displayOrder,
        String label,
        List<SnapshotSegment> segments
) {
}
