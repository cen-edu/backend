package com.cenedu.backend.domain.analysis.repository.row;

/** 학습평가에 포함된 소분류 열 정보. */
public record LearningSubcategoryColumnRow(
        Long subcategoryId,
        String subcategoryName
) {
}
