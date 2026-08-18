package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.AnalysisStatus;
import com.cenedu.backend.domain.worksheet.entity.enums.WorksheetType;

/** 학생 상세 화면 상단의 수행 요약과 취약 소분류. */
public record StudentAnalysisSummaryResponse(
        Long studentId,
        String studentName,
        String className,
        String worksheetTitle,
        WorksheetType worksheetType,
        AnalysisStatus analysisStatus,
        int totalItemCount,
        int gradedItemCount,
        int correctItemCount,
        BigDecimal performanceRate,
        BigDecimal classPerformanceRate,
        Long totalSolvingDurationMs,
        Long classAverageSolvingDurationMs,
        List<WeakSubcategory> weaknessSubcategories
) {
    public StudentAnalysisSummaryResponse {
        weaknessSubcategories = List.copyOf(weaknessSubcategories);
    }

    public record WeakSubcategory(
            Long subcategoryId,
            String subcategoryName,
            int incorrectCount,
            int gradedCount,
            BigDecimal accuracyRate
    ) {
    }
}
