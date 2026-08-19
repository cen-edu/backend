package com.cenedu.backend.domain.grading.port;

/**
 * LLM 에 넘기는 채점 기준 항목 하나.
 *
 * <p><b>가중치를 넘기지 않는다.</b> 점수는 백엔드가 계산하므로(D16) 모델이 알 이유가 없고,
 * 배점이 큰 항목을 후하게 보는 쪽으로 판정이 기울 여지를 만들지 않는다.
 */
public record RubricCriterion(long rubricItemId, String label) {
}
