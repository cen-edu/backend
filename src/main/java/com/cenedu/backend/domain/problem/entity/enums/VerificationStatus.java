package com.cenedu.backend.domain.problem.entity.enums;

/**
 * 생성 문항의 정답 검증 상태. problem_question.verification_status 의 DB 값과 이름이 일치한다.
 *
 * <p>컬럼이 null 이면 검증 대상이 아니라는 뜻이다(IMPORTED·서술형).
 */
public enum VerificationStatus {
    PENDING,
    PASSED,
    MISMATCH,
    ERROR
}
