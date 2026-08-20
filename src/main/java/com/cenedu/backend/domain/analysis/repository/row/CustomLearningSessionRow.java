package com.cenedu.backend.domain.analysis.repository.row;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.cenedu.backend.global.common.enums.CustomStage;

/**
 * 맞춤 학습 한 회차의 전체 결과와 단계별 결과를 함께 담는 조회 행.
 *
 * @param worksheetId       이 회차의 학습지. 차수 계산의 키다
 * @param parentWorksheetId 직전 차수의 학습지. 계보가 끊겼으면 {@code null} 이고 차수 미상이 된다
 * @param rootWorksheetId   원본 배정의 학습지. 차수 계산의 시작점(깊이 0)이다
 */
public record CustomLearningSessionRow(
        long customAssignmentId,
        long worksheetId,
        Long parentWorksheetId,
        long rootWorksheetId,
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
