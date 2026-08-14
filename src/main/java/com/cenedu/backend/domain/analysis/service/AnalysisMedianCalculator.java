package com.cenedu.backend.domain.analysis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Component;

/** 점수와 시간 분포에서 null을 제외한 중앙값을 계산한다. */
@Component
public class AnalysisMedianCalculator {

    /** 득점률 목록의 중앙값을 소수점 첫째 자리로 반환한다. */
    public BigDecimal scoreMedian(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream()
                .filter(value -> value != null)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle).setScale(1, RoundingMode.HALF_UP);
        }
        return sorted.get(middle - 1)
                .add(sorted.get(middle))
                .divide(BigDecimal.TWO, 1, RoundingMode.HALF_UP);
    }

    /** 밀리초 목록의 중앙값을 가장 가까운 정수 밀리초로 반환한다. */
    public Long durationMedian(List<Long> values) {
        List<Long> sorted = values.stream()
                .filter(value -> value != null)
                .sorted()
                .toList();
        if (sorted.isEmpty()) {
            return null;
        }
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 1) {
            return sorted.get(middle);
        }
        return BigDecimal.valueOf(sorted.get(middle - 1))
                .add(BigDecimal.valueOf(sorted.get(middle)))
                .divide(BigDecimal.TWO, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }
}
