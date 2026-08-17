package com.cenedu.backend.domain.problem.authoring.verification;

/** 검증기가 독립적으로 확인할 문제 정확성·조건·수정 범위 항목이다. */
public enum VerificationCheckType {
    CORRECTNESS,
    ANSWER_CONSISTENCY,
    CURRICULUM_ALIGNMENT,
    DIFFICULTY,
    EVALUATION_AREA,
    DIAGNOSTIC_TYPE,
    ASSET_CONSISTENCY,
    RUBRIC_QUALITY,
    EDIT_REQUIREMENT,
    PROTECTED_SCOPE
}
