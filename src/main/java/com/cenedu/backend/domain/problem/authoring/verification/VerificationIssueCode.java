package com.cenedu.backend.domain.problem.authoring.verification;

/** 재시도 조율측이 세부 문제 원인을 기계적으로 분류하도록 하는 검증 이슈 코드다. */
public enum VerificationIssueCode {
    ANSWER_INCORRECT,
    ANSWER_INCONSISTENT,
    CURRICULUM_MISMATCH,
    DIFFICULTY_MISMATCH,
    EVALUATION_AREA_MISMATCH,
    DIAGNOSTIC_TYPE_MISMATCH,
    ASSET_INCONSISTENT,
    RUBRIC_INVALID,
    EDIT_REQUIREMENT_MISSING,
    PROTECTED_SCOPE_CHANGED,
    SIMILARITY_INVALID,
    UNVERIFIABLE,
    PROVIDER_ERROR
}
