package com.cenedu.backend.domain.problem.authoring.model;

import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.CompareMethod;

/**
 * 채점 가능한 최소 답안 단위다.
 *
 * <p>객관식·단답형·서술형은 {@code unitKey=MAIN}, STEP_FILL은 B1, B2 형식의 키를 사용한다.
 * AI는 {@code answerRaw}를 만들고 서버 정규화기가 {@code answerNormalized}를 채운다.
 * {@code stepKey}와 {@code diagnosticType}은 STEP_FILL에서만 사용한다.
 */
public record SnapshotAnswerUnit(
        String unitKey,
        String stepKey,
        int displayOrder,
        String answerRaw,
        String answerNormalized,
        CompareMethod compareMethod,
        DiagnosticType diagnosticType,
        String displayUnit
) {
}
