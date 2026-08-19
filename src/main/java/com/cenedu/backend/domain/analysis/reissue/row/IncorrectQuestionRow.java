package com.cenedu.backend.domain.analysis.reissue.row;

import java.time.OffsetDateTime;

/**
 * 학습 흐름 전체에서 틀린 문항 하나.
 *
 * <p>같은 문항을 여러 회차에서 틀렸으면 한 행으로 모이고 {@code incorrectCount} 가 올라간다.
 */
public record IncorrectQuestionRow(
        long subUnitId,
        long questionId,
        boolean reissuable,
        int incorrectCount,
        OffsetDateTime lastIncorrectAt
) {
}
