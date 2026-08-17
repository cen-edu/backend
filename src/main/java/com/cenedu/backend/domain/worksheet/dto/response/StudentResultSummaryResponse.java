package com.cenedu.backend.domain.worksheet.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/**
 * 결과 화면 상단 요약. 문항 판정 집계와 점수를 한 자리에 모은다.
 *
 * <p>{@code empty}·{@code pending} 문항은 따로 세지 않는다.
 * {@code totalCount}에서 나머지 셋을 빼면 남는 수다.
 */
public record StudentResultSummaryResponse(
        int totalCount,
        int correctCount,
        int partialCount,
        int wrongCount,
        BigDecimal score,
        BigDecimal maxScore
) {

    /**
     * 일반·맞춤 학습은 점수 축을 {@code null}로 접는다. 배점이 없어 정오만 0/1로 기록되는데
     * 여기에 0을 넣으면 "0점 맞았다"로 읽혀 프론트가 점수 막대를 그린다.
     *
     * <p>문항 단위 {@code score}/{@code maxScore}는 일반 학습에서도 그대로 내려간다 —
     * 칸별 정오 표시에 쓰인다. 접는 것은 이 요약뿐이다.
     */
    public static StudentResultSummaryResponse from(
            List<StudentResultItemResponse> items,
            WorksheetType type,
            BigDecimal totalScore,
            BigDecimal maxTotalScore
    ) {
        boolean scored = type == WorksheetType.COMPREHENSIVE_ASSESSMENT;
        return new StudentResultSummaryResponse(
                items.size(),
                count(items, "correct"),
                count(items, "partial"),
                count(items, "wrong"),
                scored ? totalScore : null,
                scored ? maxTotalScore : null
        );
    }

    private static int count(List<StudentResultItemResponse> items, String result) {
        return (int) items.stream().filter(item -> result.equals(item.result())).count();
    }
}
