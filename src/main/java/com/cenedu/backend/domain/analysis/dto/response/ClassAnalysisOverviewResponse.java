package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 학급 분석 화면 상단의 문맥과 집계 카드. */
public record ClassAnalysisOverviewResponse(
        AnalysisContext context,
        ClassSummary summary
) {
    public record AnalysisContext(
            String worksheetTitle,
            WorksheetType worksheetType,
            String className,
            OffsetDateTime calculatedAt
    ) {
    }

    public record ClassSummary(
            int participantCount,
            int gradingPendingStudentCount,
            int gradingPendingAnswerCount,
            BigDecimal classAccuracyRate,
            Long averageSolvingDurationMs,
            Integer weaknessSubcategoryCount,
            int weaknessStudentCount
    ) {
    }
}
