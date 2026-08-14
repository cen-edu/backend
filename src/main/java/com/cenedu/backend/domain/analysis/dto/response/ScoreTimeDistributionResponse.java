package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;

/** 종합평가 학생별 득점률·총 풀이시간과 두 값의 학급 중앙값. */
public record ScoreTimeDistributionResponse(
        List<StudentDistribution> studentDistribution,
        BigDecimal medianScoreRate,
        Long medianSolvingDurationMs
) {
    public ScoreTimeDistributionResponse {
        studentDistribution = List.copyOf(studentDistribution);
    }

    public record StudentDistribution(
            Long studentId,
            String studentName,
            AnalysisStatus analysisStatus,
            BigDecimal scoreRate,
            Long totalSolvingDurationMs
    ) {
    }
}
