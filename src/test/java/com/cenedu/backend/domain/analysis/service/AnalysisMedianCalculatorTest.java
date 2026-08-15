package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalysisMedianCalculatorTest {

    private final AnalysisMedianCalculator calculator = new AnalysisMedianCalculator();

    @Test
    @DisplayName("짝수 학생의 득점률과 풀이시간은 가운데 두 값의 평균을 반환한다")
    void calculatesEvenMedian() {
        assertThat(calculator.scoreMedian(List.of(
                new BigDecimal("40.0"), new BigDecimal("80.0"))))
                .isEqualByComparingTo("60.0");
        assertThat(calculator.durationMedian(List.of(10000L, 30001L)))
                .isEqualTo(20001L);
    }

    @Test
    @DisplayName("측정값이 없으면 중앙값도 null이다")
    void returnsNullWithoutMeasuredValues() {
        assertThat(calculator.scoreMedian(List.of())).isNull();
        assertThat(calculator.durationMedian(List.of())).isNull();
    }
}
