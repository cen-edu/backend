package com.cenedu.backend.domain.analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cenedu.backend.domain.analysis.entity.enums.CutoffRule;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;

/**
 * Phase 2(유사 문항) 결과로 승급·유지·강등을 판정한다.
 *
 * <p>문항 수에 따라 기준이 갈린다. 4문항 이상이면 정답률 컷오프(80% / 40%)를 쓰고, 3문항 이하로
 * 줄었을 때는 오답 개수 컷오프로 자동 전환한다. 적은 문항에서 정답률을 쓰면 한 문항의 무게가
 * 25~100%가 되어, 실수 한 번이 강등으로 직결되기 때문이다.
 *
 * <p>1문항일 때만 오답이 강등이 아니라 유지다. 단일 관찰로는 실수와 실력 부족을 구분할 수 없다.
 *
 * <p>판정에 넣는 문항 수는 교사가 요청한 수가 아니라 <b>실제로 출제된 유사 문항 수</b>다. 재고가
 * 모자라 5문항 요청이 3문항으로 나갔다면 컷오프도 함께 오답 개수 기준으로 내려가야 한다.
 */
public final class MasteryStatusJudge {

    /** 정답률 컷오프를 쓰기 시작하는 문항 수. */
    public static final int RATIO_MIN_QUESTION_COUNT = 4;

    /** 승급 정답률 컷오프(%). */
    public static final BigDecimal CLEAR_CUTOFF = BigDecimal.valueOf(80);

    /** 강등 정답률 컷오프(%). 미만이면 강등이다. */
    public static final BigDecimal SUPPORT_CUTOFF = BigDecimal.valueOf(40);

    private static final int RATE_SCALE = 2;

    private MasteryStatusJudge() {
    }

    /**
     * 유사 문항 정답 수로 상태와 조절된 난이도를 판정한다.
     *
     * @throws IllegalArgumentException 문항이 0개이거나 정답 수가 문항 수를 넘을 때
     */
    public static Judgement judge(short difficultyBefore, int totalCount, int correctCount) {
        if (totalCount <= 0) {
            throw new IllegalArgumentException("유사 문항이 0개면 판정할 수 없습니다.");
        }
        if (correctCount < 0 || correctCount > totalCount) {
            throw new IllegalArgumentException(
                    "정답 수는 0 이상 문항 수 이하여야 합니다: " + correctCount + "/" + totalCount);
        }

        BigDecimal accuracyRate = rate(correctCount, totalCount);
        CutoffRule cutoffRule = totalCount >= RATIO_MIN_QUESTION_COUNT
                ? CutoffRule.RATIO
                : CutoffRule.WRONG_COUNT;
        MasteryStatus status = cutoffRule == CutoffRule.RATIO
                ? byRatio(accuracyRate)
                : byWrongCount(totalCount, totalCount - correctCount);
        short difficultyAfter = DifficultyLadder.apply(difficultyBefore, status);

        return new Judgement(status, cutoffRule, accuracyRate, difficultyBefore, difficultyAfter,
                isAdvancedTriggered(difficultyBefore, status));
    }

    /**
     * 정답률만으로 상태를 본다. 문항 수를 모르는 자리(단일 난이도 진단지)에서 쓴다.
     *
     * <p>4.1 의 비율 컷오프를 그대로 적용하므로, {@link #judge} 가 N≥4 에서 내리는 판정과 같다.
     */
    public static MasteryStatus judgeByRate(BigDecimal accuracyRate) {
        return byRatio(accuracyRate);
    }

    /** 정답 수를 백분율로 바꾼다. */
    public static BigDecimal rate(int correctCount, int totalCount) {
        return BigDecimal.valueOf(correctCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount), RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 4문항 이상의 정답률 컷오프. */
    private static MasteryStatus byRatio(BigDecimal accuracyRate) {
        if (accuracyRate.compareTo(CLEAR_CUTOFF) >= 0) {
            return MasteryStatus.CLEAR;
        }
        if (accuracyRate.compareTo(SUPPORT_CUTOFF) >= 0) {
            return MasteryStatus.WATCH;
        }
        return MasteryStatus.NEEDS_SUPPORT;
    }

    /** 3문항 이하의 오답 개수 컷오프. 1문항은 오답이어도 강등하지 않는다. */
    private static MasteryStatus byWrongCount(int totalCount, int wrongCount) {
        if (wrongCount == 0) {
            return MasteryStatus.CLEAR;
        }
        if (totalCount == 1 || wrongCount < totalCount) {
            return MasteryStatus.WATCH;
        }
        return MasteryStatus.NEEDS_SUPPORT;
    }

    /**
     * Phase 3(응용) 발동 조건. 상 난이도에서 승급 판정이 난 경우에만 참이다.
     *
     * <p>상 난이도는 더 올라갈 곳이 없어 조절된 난이도로는 승급 여부를 알 수 없다. 그래서 결과가
     * 아니라 판정 자체를 본다.
     */
    private static boolean isAdvancedTriggered(short difficultyBefore, MasteryStatus status) {
        return difficultyBefore == DifficultyLadder.HIGH && status == MasteryStatus.CLEAR;
    }

    /** 상태 판정 결과. */
    public record Judgement(
            MasteryStatus status,
            CutoffRule cutoffRule,
            BigDecimal accuracyRate,
            short difficultyBefore,
            short difficultyAfter,
            boolean advancedTriggered
    ) {
    }
}
