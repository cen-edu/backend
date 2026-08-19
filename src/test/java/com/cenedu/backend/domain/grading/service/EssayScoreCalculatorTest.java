package com.cenedu.backend.domain.grading.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.cenedu.backend.domain.grading.port.RubricJudgement;
import com.cenedu.backend.domain.grading.port.RubricVerdict;
import com.cenedu.backend.domain.grading.service.EssayScoreCalculator.Reason;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 가중치 환산 전수표(D15·D16).
 *
 * <p><b>가중치 합이 100 이 아닌 케이스가 이 표의 핵심이다.</b> 골든셋 10건은 전부 합 100 이라
 * 분모를 100 으로 박아도 통과한다 — DB 에는 그런 제약이 없고 문제은행 ESSAY 303건은 루브릭이
 * 0행이라 실제로는 다른 경로를 탄다. 합 60 · 130 · 0 을 손으로 넣어 그 경로를 연다.
 */
class EssayScoreCalculatorTest {

    private final EssayScoreCalculator calculator = new EssayScoreCalculator();

    private static RubricJudgement satisfied(long id) {
        return new RubricJudgement(id, RubricVerdict.SATISFIED, "");
    }

    private static RubricJudgement notSatisfied(long id) {
        return new RubricJudgement(id, RubricVerdict.NOT_SATISFIED, "");
    }

    @Test
    @DisplayName("분모는 그 문항 가중치의 실제 합이다 — 100 이 아니다")
    void denominatorIsActualWeightSum() {
        Map<Long, Short> sum60 = Map.of(1L, (short) 30, 2L, (short) 30);
        // 합이 60 인 문항에서 전 항목 충족은 만점이다. 분모를 100 으로 박으면 6.00 이 된다.
        assertThat(calculator.calculate(BigDecimal.TEN, sum60, List.of(satisfied(1), satisfied(2))).score())
                .isEqualByComparingTo("10.00");
        assertThat(calculator.calculate(BigDecimal.TEN, sum60, List.of(satisfied(1), notSatisfied(2))).score())
                .isEqualByComparingTo("5.00");

        Map<Long, Short> sum130 = Map.of(1L, (short) 40, 2L, (short) 90);
        assertThat(calculator.calculate(BigDecimal.TEN, sum130, List.of(satisfied(1), notSatisfied(2))).score())
                .isEqualByComparingTo("3.07");
        assertThat(calculator.calculate(BigDecimal.TEN, sum130, List.of(satisfied(1), satisfied(2))).score())
                .isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("나누어떨어지지 않으면 소수 둘째 자리에서 버린다 — 반올림은 없는 점수를 준다")
    void truncatesInsteadOfRounding() {
        Map<Long, Short> sum130 = Map.of(1L, (short) 40, 2L, (short) 90);
        // 10 × 40/130 = 3.0769… → 3.07
        assertThat(calculator.calculate(BigDecimal.TEN, sum130, List.of(satisfied(1))).score())
                .isEqualByComparingTo("3.07");
        // 10 × 90/130 = 6.923… → 6.92
        assertThat(calculator.calculate(BigDecimal.TEN, sum130, List.of(satisfied(2))).score())
                .isEqualByComparingTo("6.92");
    }

    @Test
    @DisplayName("가중치 합이 0 이면 점수를 내지 않는다 — 루브릭 0행 문항이 이 경로다")
    void refusesWhenNoWeight() {
        EssayScoreCalculator.Outcome outcome =
                calculator.calculate(BigDecimal.TEN, Map.of(), List.of());
        assertThat(outcome.isScored()).isFalse();
        assertThat(outcome.reason()).isEqualTo(Reason.NO_WEIGHT);
        assertThat(outcome.score()).isNull();

        assertThat(calculator.calculate(BigDecimal.TEN, Map.of(1L, (short) 0), List.of(satisfied(1))).reason())
                .isEqualTo(Reason.NO_WEIGHT);
    }

    @Test
    @DisplayName("판정 불가가 하나라도 있으면 점수를 내지 않는다 — 그 칸은 교사 수동이다")
    void refusesWhenAnyUnjudgeable() {
        EssayScoreCalculator.Outcome outcome = calculator.calculate(
                BigDecimal.TEN,
                Map.of(1L, (short) 50, 2L, (short) 50),
                List.of(satisfied(1), new RubricJudgement(2, RubricVerdict.UNJUDGEABLE, "")));

        assertThat(outcome.reason()).isEqualTo(Reason.UNJUDGEABLE_PRESENT);
        assertThat(outcome.score()).isNull();
    }

    @Test
    @DisplayName("문항 배점이 없으면 점수를 내지 않는다 — 만점 1.00 가정으로 소수 점수를 만들지 않는다")
    void refusesWhenNoMaxScore() {
        EssayScoreCalculator.Outcome outcome = calculator.calculate(
                null, Map.of(1L, (short) 50, 2L, (short) 50), List.of(satisfied(1), notSatisfied(2)));

        assertThat(outcome.reason()).isEqualTo(Reason.NO_MAX_SCORE);
        assertThat(outcome.score()).isNull();
    }

    @Test
    @DisplayName("판정이 없는 항목은 분자에 들어가지 않는다")
    void countsOnlySatisfiedWeights() {
        assertThat(calculator.calculate(
                BigDecimal.TEN,
                Map.of(1L, (short) 50, 2L, (short) 50),
                List.of(satisfied(1))).score())
                .isEqualByComparingTo("5.00");
    }
}
