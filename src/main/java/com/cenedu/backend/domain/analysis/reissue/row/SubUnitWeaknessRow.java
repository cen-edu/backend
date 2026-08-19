package com.cenedu.backend.domain.analysis.reissue.row;

/** 한 소단원의 누적 오답 규모. 평가 영역이 없는 문항의 오답도 포함한다. */
public record SubUnitWeaknessRow(
        long subUnitId,
        int historicalIncorrectItemCount,
        int incorrectSessionCount
) {
}
