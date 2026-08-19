package com.cenedu.backend.domain.analysis.service;

import java.math.BigDecimal;

/**
 * 초기 진단 평가의 영점 조절.
 *
 * <p>교사가 상·중·하를 어떤 비율로 섞어 출제하더라도 같은 기준으로 실력을 판별하도록, 난이도를
 * 배점(하 2점, 중 3점, 상 4점)으로 환산해 가중 성취도를 낸다. 문항 수 비율이 아니라 배점 비율로
 * 나누기 때문에 고배점 문항을 맞힌 학생이 제대로 위로 간다.
 *
 * <p>배점 폭을 1~3 이 아니라 2~4 로 잡은 것은 수능 배점을 따른 것이다. 최저 배점이 0 에 가까울수록
 * 쉬운 문항을 찍어 맞힌 것이 성취도를 크게 흔든다. 바닥을 2 로 올리면 난이도 간 배점 비가 완만해져
 * 우연한 정답의 왜곡이 줄어든다.
 *
 * <p>컷오프는 80% / 40% 다. 상위 기준을 메인 평가의 승급(CLEAR) 컷오프와 같은 80% 로 맞춰,
 * 진단으로 '상'에 배정되는 학생과 승급으로 '상'에 올라오는 학생이 같은 잣대를 통과하게 한다.
 * 40% 미만을 하로 보내는 것은 문항반응이론의 기본 추측도(20~25%)를 고려하면 그 구간의 정답이
 * 실력을 뜻한다고 보기 어렵기 때문이다.
 *
 * <h2>진단지가 한 가지 난이도만으로 출제된 경우</h2>
 *
 * <p>가중 공식은 혼합 출제를 전제한다. 한 가지 난이도로만 출제된 진단지에서는 가중치가 상수가
 * 되어 성취도가 그냥 정답률이 되는데, 여기에 절대 컷오프를 그대로 적용하면 <b>하 난이도만 풀어
 * 다 맞힌 학생이 100% 로 '상'에 배정된다</b>. 푼 적 없는 난이도를 실력으로 인정하는 셈이다.
 *
 * <p>그래서 단일 난이도일 때는 절대 컷오프 대신 <b>출제된 난이도를 기준으로 한 상대 조정</b>을
 * 한다. 정답률에 같은 80% / 40% 컷오프를 적용하되, 결과를 그 난이도에서 한 칸 올리거나 내리는
 * 데 쓴다. 관찰된 것이 "중 난이도를 잘 푼다"면 결론도 "중의 한 칸 위"까지만 간다.
 *
 * <p>이 상대 조정은 설계서 2.3 의 '중' 난이도 예시와 같은 값을 낸다(80% 이상 → 상, 40~79% →
 * 중, 40% 미만 → 하). 예시에 없는 '하'·'상' 단일 출제에서 절대 해석과 갈리며, 그때 위 문단의
 * 이유로 상대 해석을 택한다.
 */
public final class PlacementScorer {

    /** 하 난이도 문항의 배점. */
    public static final int LOW_SCORE = 2;

    /** 중 난이도 문항의 배점. */
    public static final int MID_SCORE = 3;

    /** 상 난이도 문항의 배점. */
    public static final int HIGH_SCORE = 4;

    /** 혼합 진단지의 상 난이도 배정 컷오프(%). 메인 평가의 승급 컷오프와 같은 값이다. */
    public static final BigDecimal HIGH_CUTOFF = MasteryStatusJudge.CLEAR_CUTOFF;

    /** 혼합 진단지의 중 난이도 배정 컷오프(%). 미만이면 하로 배정한다. */
    public static final BigDecimal MID_CUTOFF = MasteryStatusJudge.SUPPORT_CUTOFF;

    private PlacementScorer() {
    }

    /**
     * 난이도별 출제 수와 정답 수로 가중 성취도와 시작 난이도를 산출한다.
     *
     * <p>채점이 끝난 문항만 넘겨야 한다. 미채점 문항을 오답으로 세면 안 푼 학생이 기초 부족으로
     * 기록된다.
     *
     * @return 출제된 문항이 하나도 없으면 {@code null}. 이때는 판별할 근거가 없으므로 임의의
     *         난이도를 찍지 않는다.
     */
    public static PlacementResult score(PlacementTally tally) {
        int maxScore = LOW_SCORE * tally.lowTotal()
                + MID_SCORE * tally.midTotal()
                + HIGH_SCORE * tally.highTotal();
        if (maxScore == 0) {
            return null;
        }

        int earnedScore = LOW_SCORE * tally.lowCorrect()
                + MID_SCORE * tally.midCorrect()
                + HIGH_SCORE * tally.highCorrect();
        BigDecimal rate = MasteryStatusJudge.rate(earnedScore, maxScore);

        Short soleDifficulty = tally.soleDifficulty();
        short difficulty = soleDifficulty == null
                ? byAbsoluteCutoff(rate)
                : DifficultyLadder.apply(soleDifficulty, MasteryStatusJudge.judgeByRate(rate));

        return new PlacementResult(rate, difficulty, earnedScore, maxScore, soleDifficulty == null);
    }

    /** 혼합 진단지의 절대 컷오프. */
    private static short byAbsoluteCutoff(BigDecimal rate) {
        if (rate.compareTo(HIGH_CUTOFF) >= 0) {
            return DifficultyLadder.HIGH;
        }
        if (rate.compareTo(MID_CUTOFF) >= 0) {
            return DifficultyLadder.MID;
        }
        return DifficultyLadder.LOW;
    }

    /** 진단 평가 한 학생·한 소단원의 난이도별 채점 완료 문항 수와 완전 정답 수. */
    public record PlacementTally(
            int lowTotal,
            int lowCorrect,
            int midTotal,
            int midCorrect,
            int highTotal,
            int highCorrect
    ) {

        /** 출제된 난이도가 하나뿐이면 그 값, 섞여 있거나 비었으면 {@code null}. */
        Short soleDifficulty() {
            Short sole = null;
            int bands = 0;
            if (lowTotal > 0) {
                sole = DifficultyLadder.LOW;
                bands++;
            }
            if (midTotal > 0) {
                sole = DifficultyLadder.MID;
                bands++;
            }
            if (highTotal > 0) {
                sole = DifficultyLadder.HIGH;
                bands++;
            }
            return bands == 1 ? sole : null;
        }
    }

    /**
     * 영점 조절 산출 결과.
     *
     * @param mixed 진단지가 두 가지 이상의 난이도로 출제됐는지. 거짓이면 절대 컷오프가 아니라
     *              단일 난이도 기준의 상대 조정으로 나온 값이다.
     */
    public record PlacementResult(
            BigDecimal rate,
            short difficulty,
            int earnedScore,
            int maxScore,
            boolean mixed
    ) {
    }
}
