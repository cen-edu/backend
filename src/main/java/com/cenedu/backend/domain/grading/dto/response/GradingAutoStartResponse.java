package com.cenedu.backend.domain.grading.dto.response;

import java.time.OffsetDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 자동채점 실행 접수(명세 7절). 실제 채점은 백그라운드에서 돈다.
 *
 * @param targetAnswerCount 문항 수가 아니라 <b>채점 칸 수</b>다
 * @param skippedCount      교사가 이미 점수를 고쳐 대상에서 빠진 칸 수. 이 값이 없으면 교사가
 *                          "왜 일부만 채점됐지"를 알 수 없다
 */
public record GradingAutoStartResponse(

        @Schema(description = "채점 대상 칸 수")
        int targetAnswerCount,

        @Schema(description = "교사 수정분이라 제외된 칸 수")
        int skippedCount,

        OffsetDateTime startedAt
) {
}
