package com.cenedu.backend.domain.worksheet.dto.response;

import com.cenedu.backend.domain.worksheet.repository.row.LearningStatusSummaryRow;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 요약 카드 4개.
 *
 * <p>{@code submitted}·{@code inProgress}·{@code unsubmitted}는 <b>연인원</b>이다. 한 학생이
 * 학습지 셋에 배정되어 둘을 냈으면 {@code submitted}에 2로 센다. 프론트 라벨이 "명"이라
 * "건"으로 바꾸는 요청이 필요하다(명세 7-①).
 */
public record LearningStatusSummaryResponse(
        @Schema(description = "진행 중이고 맞춤이 아닌 배포 수")
        int assignments,

        @Schema(description = "제출 완료 배정 건수(연인원)")
        long submitted,

        @Schema(description = "풀이 중 배정 건수(연인원). 마감 전만 센다")
        long inProgress,

        @Schema(description = "미제출 배정 건수(연인원). 확정 값과 마감 경과 파생을 모두 센다")
        long unsubmitted
) {

    public static LearningStatusSummaryResponse of(int assignments, LearningStatusSummaryRow row) {
        // 행이 하나도 없으면 집계 결과가 null 이다.
        if (row == null) {
            return new LearningStatusSummaryResponse(assignments, 0L, 0L, 0L);
        }
        return new LearningStatusSummaryResponse(
                assignments, row.submitted(), row.inProgress(), row.unsubmitted());
    }
}
