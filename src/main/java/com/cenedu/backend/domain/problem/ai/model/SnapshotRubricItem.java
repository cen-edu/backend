package com.cenedu.backend.domain.problem.ai.model;

/**
 * 서술형 풀이 과정을 분석적으로 채점하는 독립 평가 기준 한 개다.
 *
 * <p>{@code rubricKey}는 버전 간 안정적인 논리 키다. {@code weightPercent}는 문항 점수 안에서의
 * 상대 비율이며, 모든 루브릭 항목의 합은 100이어야 한다. 교사 승인 정보는 버전에 저장한다.
 */
public record SnapshotRubricItem(
        String rubricKey,
        int displayOrder,
        String criterion,
        int weightPercent
) {
}
