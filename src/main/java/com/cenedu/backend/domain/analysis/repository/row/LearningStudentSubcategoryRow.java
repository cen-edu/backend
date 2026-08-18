package com.cenedu.backend.domain.analysis.repository.row;

/** 학생 한 명의 소분류별 정답 수와 채점 완료 수. */
public record LearningStudentSubcategoryRow(
        Long studentId,
        String studentName,
        Long subcategoryId,
        int correctCount,
        int gradedCount
) {
}
