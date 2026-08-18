package com.cenedu.backend.domain.analysis.dto.response;

import java.util.List;

/** 학습평가의 소분류 열, 학생별 성취 행렬과 소분류 취약 순위. */
public record LearningAssessmentAchievementResponse(
        List<SubcategoryColumn> subcategories,
        List<LearningAssessmentStudentAchievement> students,
        List<SubcategoryWeakness> subcategoryRanking
) {
    public LearningAssessmentAchievementResponse {
        subcategories = List.copyOf(subcategories);
        students = List.copyOf(students);
        subcategoryRanking = List.copyOf(subcategoryRanking);
    }

    public record SubcategoryColumn(
            Long subcategoryId,
            String subcategoryName
    ) {
    }

    public record LearningAssessmentStudentAchievement(
            Long studentId,
            String studentName,
            List<SubcategoryResult> results
    ) {
        public LearningAssessmentStudentAchievement {
            results = List.copyOf(results);
        }
    }

    public record SubcategoryResult(
            Long subcategoryId,
            int correctCount,
            int gradedCount
    ) {
    }

    public record SubcategoryWeakness(
            Long subcategoryId,
            String subcategoryName,
            int weakStudentCount
    ) {
    }
}
