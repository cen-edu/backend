package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalysisStatusClassifierTest {

    private final AnalysisStatusClassifier classifier = new AnalysisStatusClassifier();

    @ParameterizedTest(name = "채점 {0}문항, 정답률 {1}이면 {2}")
    @CsvSource({
            "0, 0, INSUFFICIENT_DATA",
            "1, 59.9, INTENSIVE",
            "1, 60.0, REVIEW",
            "1, 79.9, REVIEW",
            "1, 80.0, STABLE"
    })
    @DisplayName("정답률 경계값으로 학생 분석 상태를 분류한다")
    void classifiesByAccuracyThreshold(
            int gradedItemCount,
            String accuracyRate,
            AnalysisStatus expected
    ) {
        assertThat(classifier.classify(
                gradedItemCount, new BigDecimal(accuracyRate))).isEqualTo(expected);
    }
}
