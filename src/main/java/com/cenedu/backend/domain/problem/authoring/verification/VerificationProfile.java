package com.cenedu.backend.domain.problem.authoring.verification;

/** Repair 대상별로 허용된 검증 조합과 LLM 호출 상한을 고정한다. */
public enum VerificationProfile {
    FULL_CONTENT,
    ANSWER_RELATED,
    ORIGINAL_ONLY,
    RUBRIC_ONLY
}
