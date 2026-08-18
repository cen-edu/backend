package com.cenedu.backend.domain.worksheet.dto.response;

import java.util.List;

/** 학습 현황 첫 화면. 요약 카드와 배포 목록을 함께 낸다. */
public record LearningStatusListResponse(
        LearningStatusSummaryResponse summary,
        List<LearningStatusAssignmentResponse> assignments
) {

    public static LearningStatusListResponse of(
            LearningStatusSummaryResponse summary,
            List<LearningStatusAssignmentResponse> assignments
    ) {
        return new LearningStatusListResponse(summary, List.copyOf(assignments));
    }
}
