package com.cenedu.backend.domain.analysis.repository.row;

/** 소분류별로 한 문항 이상 틀린 학생 수 집계 행. */
public record LearningSubcategoryWeaknessRow(
        Long subcategoryId,
        String subcategoryName,
        int weakStudentCount
) {
}
