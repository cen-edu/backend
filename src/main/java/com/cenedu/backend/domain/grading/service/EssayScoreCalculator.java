package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.grading.port.RubricJudgement;
import com.cenedu.backend.domain.grading.port.RubricVerdict;

import org.springframework.stereotype.Component;

/**
 * 루브릭 판정을 점수로 환산한다. <b>LLM 에 맡기지 않는다</b>(D16).
 *
 * <p><b>분모는 그 문항 가중치의 실제 합이다. 100 이 아니다</b>(D15). 골든셋 10건이 전부 합 100
 * 이지만 DB 에는 그런 제약이 없고, 합이 100 이 아닌 문항에 100 을 박으면 점수가 조용히 틀린다 —
 * 합 60 인 문항은 전 항목을 충족해도 60%밖에 못 받는다.
 *
 * <p><b>{@code double} 을 쓰지 않는다.</b> 이미 99.99 / 100.01 사례가 있다. 자릿수는
 * {@code auto_score} 컬럼에 맞춰 소수 둘째 자리에서 {@link RoundingMode#DOWN} 으로 버린다 —
 * 반올림은 학생에게 없는 점수를 준다.
 */
@Component
public class EssayScoreCalculator {

    /** {@code auto_score numeric(5,2)} 에 맞춘 소수 자릿수. */
    private static final int SCALE = 2;

    /**
     * 환산 결과. 점수를 낼 수 없을 때 <b>왜</b> 못 냈는지가 교사에게 남길 사유를 가른다.
     *
     * @param score 점수. {@link Reason#SCORED} 일 때만 값이 있다
     */
    public record Outcome(BigDecimal score, Reason reason) {

        public boolean isScored() {
            return reason == Reason.SCORED;
        }

        private static Outcome scored(BigDecimal score) {
            return new Outcome(score, Reason.SCORED);
        }

        private static Outcome unscored(Reason reason) {
            return new Outcome(null, reason);
        }
    }

    /** 점수를 못 낸 이유. 일시적인 것이 아니라 전부 구조적인 것이다 — 다시 돌려도 같다. */
    public enum Reason {
        SCORED,
        /** 판정 불가 항목이 하나라도 있다. 그 칸은 점수 없이 교사 수동으로 간다(D14). */
        UNJUDGEABLE_PRESENT,
        /** 그 문항의 가중치 합이 0 이하다. 문제은행 ESSAY 303건이 루브릭 0행이라 이 경로를 탄다. */
        NO_WEIGHT,
        /** 문항 배점이 없다(일반·맞춤 학습). 만점 1.00 가정으로 소수 점수를 만들지 않는다 */
        NO_MAX_SCORE
    }

    /**
     * 판정과 가중치로 점수를 낸다.
     *
     * @param maxScore   문항 배점. {@code null}이면 점수를 내지 않는다 — 규칙 채점은 만점을
     *                   {@code 1.00}으로 보지만, 거기에 가중 비율을 곱하면 {@code 0.60} 같은
     *                   값이 나온다. 그것이 의도인지 확인되기 전에는 계산하지 않는다
     * @param weights    항목 ID 별 가중치. <b>이 맵의 합이 분모다</b>
     * @param judgements 항목별 판정. 여기 없는 항목은 미충족이 아니라 분자에 안 들어갈 뿐이다
     */
    public Outcome calculate(BigDecimal maxScore, Map<Long, Short> weights,
                             List<RubricJudgement> judgements) {
        if (judgements.stream().anyMatch(j -> j.verdict() == RubricVerdict.UNJUDGEABLE)) {
            return Outcome.unscored(Reason.UNJUDGEABLE_PRESENT);
        }
        int denominator = weights.values().stream().mapToInt(Short::intValue).sum();
        if (denominator <= 0) {
            return Outcome.unscored(Reason.NO_WEIGHT);
        }
        if (maxScore == null) {
            return Outcome.unscored(Reason.NO_MAX_SCORE);
        }
        int numerator = judgements.stream()
                .filter(j -> j.verdict() == RubricVerdict.SATISFIED)
                .map(j -> weights.get(j.rubricItemId()))
                .filter(weight -> weight != null)
                .mapToInt(Short::intValue)
                .sum();

        // 곱한 뒤에 나눈다. 먼저 나누면 비율에서 자릿수가 잘려 배점이 큰 문항일수록 오차가 커진다.
        BigDecimal score = maxScore
                .multiply(BigDecimal.valueOf(numerator))
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.DOWN);
        return Outcome.scored(score);
    }
}
