package com.cenedu.backend.domain.analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 학생 한 명의 회차 상세. 화면의 문항 매트릭스가 {@code attempts} 를 읽는다. */
public record StudentDetail(
        String assessmentId,
        String assessmentTitle,
        LocalDate assessmentDate,
        String assessmentType,
        boolean simulation,
        int participantCount,
        int classCorrectRatePercent,
        ClassDashboard.StudentRow student,
        List<AreaRow> areas,
        List<AttemptRow> attempts,
        List<ClassDashboard.DifficultyRow> difficulties
) {

    public record AreaRow(
            String evaluationArea,
            int totalCount,
            int correctCount,
            int correctRatePercent,
            int classTotalCount,
            int classCorrectCount,
            int classCorrectRatePercent
    ) {
    }

    public record AttemptRow(
            int problemNumber,
            String problemId,
            String problemTitle,
            String problemText,
            String evaluationArea,
            boolean correct,
            boolean hintUsed,
            BigDecimal referenceSuccessRate,
            String difficultyBand,
            String sourceDataset,
            String sourceDifficulty,
            String difficultyBasis,
            int classCorrectRatePercent
    ) {
    }
}
