package com.cenedu.backend.domain.analysis.reissue.row;

/** 학생이 학습 흐름 안에서 이미 받은 문항. 중복 출제를 막는 데 쓴다. */
public record QuestionOwnershipRow(
        long subUnitId,
        long questionId
) {
}
