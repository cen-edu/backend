package com.cenedu.backend.domain.problem.authoring.port;

import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationRequest;

/**
 * Problem 조율측과 배세빈 팀원의 {@code ai.verification.adapter} 구현체 사이의 검증 경계다.
 *
 * <p>계약의 소유자는 이하영, Adapter 구현 소유자는 배세빈이다. 필드를 변경할 때는
 * 두 담당자가 먼저 합의하고, 검증기는 저장·재시도·승격 정책을 반환하지 않는다.
 */
public interface ProblemVerificationPort {

    /** 재시도·저장을 결정하지 않고 요청한 범위의 검증 결과만 반환한다. */
    ProblemVerificationReport verify(ProblemVerificationRequest request);
}
