package com.cenedu.backend.domain.analysis.service;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import org.springframework.stereotype.Component;

/** 채점 완료 문항 수와 정답률을 지도 우선순위 상태로 변환한다. */
@Component
public class AnalysisStatusClassifier {

    private static final BigDecimal REVIEW_THRESHOLD = BigDecimal.valueOf(60);
    private static final BigDecimal STABLE_THRESHOLD = BigDecimal.valueOf(80);

    /** 자료가 없으면 자료 부족, 이후 60%와 80%를 경계로 상태를 분류한다. */
    public AnalysisStatus classify(int gradedItemCount, BigDecimal accuracyRate) {
        if (gradedItemCount == 0 || accuracyRate == null) {
            return AnalysisStatus.INSUFFICIENT_DATA;
        }
        if (accuracyRate.compareTo(REVIEW_THRESHOLD) < 0) {
            return AnalysisStatus.INTENSIVE;
        }
        if (accuracyRate.compareTo(STABLE_THRESHOLD) < 0) {
            return AnalysisStatus.REVIEW;
        }
        return AnalysisStatus.STABLE;
    }
}
