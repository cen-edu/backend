package com.cenedu.backend.domain.submission.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** 문항 하나의 답안 일괄 저장. 문항 이탈 시 1회 호출을 전제한다(명세 6절). */
public record StudentAnswerSaveRequest(
        @NotNull(message = "timeSpentSeconds는 필수입니다.")
        @PositiveOrZero(message = "timeSpentSeconds는 0 이상이어야 합니다.")
        Integer timeSpentSeconds,

        @NotNull(message = "answers는 필수입니다.")
        @Valid
        List<StudentAnswerUnitSaveRequest> answers
) {
}
