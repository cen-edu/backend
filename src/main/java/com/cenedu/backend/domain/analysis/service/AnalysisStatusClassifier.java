package com.cenedu.backend.domain.analysis.service;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import org.springframework.stereotype.Component;

/** 채점 완료 문항 수와 성취율을 지도 우선순위 상태로 변환한다. */
@Component
public class AnalysisStatusClassifier {

    private static final BigDecimal REVIEW_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal STABLE_THRESHOLD = BigDecimal.valueOf(80);

    /** 자료가 없으면 자료 부족, 이후 60%와 80%를 경계로 상태를 분류한다. */
    public AnalysisStatus classify(int gradedItemCount, BigDecimal performanceRate) {
        if (gradedItemCount == 0 || performanceRate == null) {
            return AnalysisStatus.INSUFFICIENT_DATA;
        }
        if (performanceRate.compareTo(REVIEW_THRESHOLD) < 0) {
            return AnalysisStatus.INTENSIVE;
        }
        if (performanceRate.compareTo(STABLE_THRESHOLD) < 0) {
            return AnalysisStatus.REVIEW;
        }
        return AnalysisStatus.STABLE;
    }
}
