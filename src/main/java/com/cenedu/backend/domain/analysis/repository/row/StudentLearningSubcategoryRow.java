package com.cenedu.backend.domain.analysis.repository.row;

/** 학습평가 선택 학생의 소분류별 정답 수와 채점 완료 문항 수 집계 행. */
public record StudentLearningSubcategoryRow(
        Long subcategoryId,
        String subcategoryName,
        int correctCount,
        int gradedCount
) {
}
