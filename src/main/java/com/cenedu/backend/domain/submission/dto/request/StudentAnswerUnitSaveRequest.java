package com.cenedu.backend.domain.submission.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 채점 칸 하나의 답안. {@code normalized}·{@code answerImageRef}·{@code compareMethod}·점수 계열은
 * 받지 않는다 — 서버가 결정하거나 조립한다(명세 6절).
 */
public record StudentAnswerUnitSaveRequest(
        @NotNull(message = "answerUnitId는 필수입니다.")
        Long answerUnitId,

        Long selectedChoiceId,

        String rawLatex,

        @NotNull(message = "hasHandwriting은 필수입니다.")
        Boolean hasHandwriting
) {
}
