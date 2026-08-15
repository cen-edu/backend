package com.cenedu.backend.domain.analysis.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.cenedu.backend.domain.analysis.entity.enums.CustomResolutionStatus;
import com.cenedu.backend.domain.analysis.entity.enums.DifficultyBand;
import com.cenedu.backend.domain.worksheet.entity.enums.CustomStage;

/** 학생 상세 화면의 맞춤 학습 회차별 결과. */
public record CustomLearningSessionListResponse(
        List<CustomLearningSession> sessions
) {
    public CustomLearningSessionListResponse {
        sessions = List.copyOf(sessions);
    }

    public record CustomLearningSession(
            long customAssignmentId,
            CustomResolutionStatus overallResolutionStatus,
            OffsetDateTime assignedAt,
            OffsetDateTime completedAt,
            int completedItemCount,
            int totalItemCount,
            BigDecimal accuracyRate,
            List<CustomSubcategoryResult> subcategories
    ) {
        public CustomLearningSession {
            subcategories = List.copyOf(subcategories);
        }
    }

    public record CustomSubcategoryResult(
            long subcategoryId,
            String subcategoryName,
            CustomResolutionStatus resolutionStatus,
            DifficultyBand currentDifficultyBand,
            int completedItemCount,
            int totalItemCount,
            BigDecimal sourceAccuracyRate,
            BigDecimal accuracyRate,
            List<CustomStageResult> stages
    ) {
        public CustomSubcategoryResult {
            stages = List.copyOf(stages);
        }
    }

    public record CustomStageResult(
            CustomStage stage,
            int correctCount,
            int totalCount
    ) {
    }
}
