package com.cenedu.backend.domain.grading.port;

/**
 * 기준 항목 하나에 대한 판정 결과.
 *
 * @param evidence 그렇게 본 근거. 학생 필기에서 인용한 조각이 들어오므로 로그에 남기지 않는다
 */
public record RubricJudgement(long rubricItemId, RubricVerdict verdict, String evidence) {
}
