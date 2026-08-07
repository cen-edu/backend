package com.cenedu.backend.domain.analysis.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 취약점 분석 화면이 쓰는 학급 집계.
 *
 * <p>필드 이름과 구조는 프론트 연동 계층(`src/api/weaknessBackend.js`)이 읽는 그대로다.
 * 화면이 이미 이 모양에 맞춰져 있어 여기서 이름을 바꾸면 화면이 조용히 빈칸이 된다.
 */
public record ClassDashboard(
        String assessmentId,
        String assessmentTitle,
        LocalDate assessmentDate,
        String assessmentType,
        boolean simulation,
        Overall overall,
        List<StudentRow> students,
        List<ProblemRow> problems,
        List<DifficultyRow> difficulties,
        List<ClassAreaRow> areas
) {

    public record Overall(
            int studentCount,
            int problemCount,
            int attemptCount,
            int correctCount,
            int correctRatePercent,
            int hintCount,
            long attentionStudentCount
    ) {
    }

    public record StudentRow(
            String studentId,
            String studentName,
            int totalCount,
            int correctCount,
            int correctRatePercent,
            int hintCount,
            String status
    ) {
    }

    public record ProblemRow(
            int problemNumber,
            String problemId,
            String problemTitle,
            String evaluationArea,
            String topic,
            String sourceDataset,
            String difficultyBand,
            BigDecimal referenceSuccessRate,
            int totalCount,
            int correctCount,
            int classCorrectRatePercent
    ) {
    }

    public record DifficultyRow(
            String difficultyBand,
            int problemCount,
            int totalCount,
            int correctCount,
            int correctRatePercent
    ) {
    }

    public record ClassAreaRow(
            String evaluationArea,
            int problemCount,
            int totalCount,
            int correctCount,
            int correctRatePercent
    ) {
    }
}
