package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.StudentItemResultType;
import com.cenedu.backend.domain.submission.entity.enums.GradingStatus;

/** 학생 문항 결과와 문항별 학급 통계를 함께 조회한 행. */
public record StudentItemDetailRow(
        Long worksheetItemId,
        Long questionId,
        int itemNumber,
        String questionTitle,
        String questionType,
        String evaluationArea,
        int sourceDifficulty,
        GradingStatus gradingStatus,
        StudentItemResultType resultType,
        BigDecimal score,
        BigDecimal maxScore,
        Long solvingDurationMs,
        Long classMedianSolvingDurationMs,
        int correctStudentCount,
        int gradedStudentCount,
        BigDecimal classAccuracyRate
) {
}
