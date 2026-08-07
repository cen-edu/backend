package com.cenedu.backend.domain.analysis.dto;

import java.time.LocalDate;

/** 학습지 선택 목록 한 줄. 프론트는 여기서 {@code assessmentId} 와 {@code problemCount} 를 쓴다. */
public record AssessmentListItem(
        String assessmentId,
        String assessmentTitle,
        LocalDate assessmentDate,
        String assessmentType,
        boolean simulation,
        int studentCount,
        int problemCount,
        int attemptCount,
        int correctRatePercent,
        int lowCount,
        int mediumCount,
        int highCount
) {
}
