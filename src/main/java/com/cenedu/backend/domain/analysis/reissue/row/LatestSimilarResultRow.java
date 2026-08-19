package com.cenedu.backend.domain.analysis.reissue.row;

/**
 * 직전 맞춤 회차 한 소단원의 유사 문항 결과.
 *
 * <p>난이도 조절은 뿌리까지 거슬러 올라가지 않고 이 한 회차만 본다. {@code difficulty} 는 그
 * 회차에 실제로 출제된 유사 문항의 난이도이고, {@code gradedCount} 는 판정에 쓰는 N 이다.
 */
public record LatestSimilarResultRow(
        long subUnitId,
        short difficulty,
        int gradedCount,
        int correctCount
) {
}
