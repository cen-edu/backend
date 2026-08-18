package com.cenedu.backend.domain.problem.authoring.model;

import com.cenedu.backend.domain.problem.entity.enums.QuestionPresentation;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

/**
 * 문항의 교육과정 분류와 표현 방식을 담는다.
 *
 * <p>{@code subUnitId}, {@code topicCode}, {@code derivedFromQuestionId}는 서버가 생성 요청에서
 * 제공하며 에이전트가 임의로 바꾸지 않는다. {@code difficulty}는 {@code low}, {@code mid},
 * {@code high} 중 하나를 사용한다. 현재 AGENTS.md 의 제약사항에 따라 low,mid,high 형태로 정의하였으나,
 * 영속화 Mapper를 통해 기존 DB의 1, 2, 3 값으로 변환한다.
 */
public record SnapshotMetadata(
        QuestionType questionType,
        QuestionPresentation presentation,
        String difficulty,
        Long subUnitId,
        String topicCode,
        EvaluationArea evaluationArea,
        Long derivedFromQuestionId
) {
}
