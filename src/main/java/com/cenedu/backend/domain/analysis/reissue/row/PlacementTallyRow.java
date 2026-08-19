package com.cenedu.backend.domain.analysis.reissue.row;

/** 원본 배정 한 소단원의 난이도별 채점 완료 수와 완전 정답 수. */
public record PlacementTallyRow(
        long subUnitId,
        short difficulty,
        int gradedCount,
        int correctCount
) {
}
