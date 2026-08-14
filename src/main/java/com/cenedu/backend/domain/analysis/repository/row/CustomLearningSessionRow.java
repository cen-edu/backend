package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;

/** 맞춤 학습 한 회차의 전체 결과와 단계별 결과를 함께 담는 조회 행. */
public record CustomLearningSessionRow(
        long customAssignmentId,
        OffsetDateTime assignedAt,
        OffsetDateTime completedAt,
        int sessionCompletedItemCount,
        int sessionTotalItemCount,
        BigDecimal sessionAccuracyRate,
        long subcategoryId,
        String subcategoryName,
        Integer currentDifficulty,
        int subcategoryCompletedItemCount,
        int subcategoryTotalItemCount,
        BigDecimal sourceAccuracyRate,
        BigDecimal accuracyRate,
        int diagnosticCompletedItemCount,
        int diagnosticTotalItemCount,
        int diagnosticCorrectItemCount,
        CustomStage customStage,
        int stageCorrectCount,
        int stageTotalCount
) {
}
