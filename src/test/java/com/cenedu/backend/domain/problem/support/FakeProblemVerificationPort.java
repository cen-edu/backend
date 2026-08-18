package com.cenedu.backend.domain.problem.support;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.port.ProblemVerificationPort;
import com.cenedu.backend.domain.problem.authoring.verification.*;

/** 검증 결과만 반환하고 저장·재시도·승격은 수행하지 않는 테스트 Fake다. */
public final class FakeProblemVerificationPort implements ProblemVerificationPort {
    private final boolean passed;
    private int calls;

    public FakeProblemVerificationPort(boolean passed) {
        this.passed = passed;
    }

    /** Fake 검증 호출 횟수를 반환한다. */
    public int callCount() {
        return calls;
    }

    /** 요청의 ID와 범위를 보존한 검증 보고서만 반환한다. */
    @Override
    public ProblemVerificationReport verify(ProblemVerificationRequest request) {
        calls++;
        VerificationOverallStatus status = passed
                ? VerificationOverallStatus.PASSED : VerificationOverallStatus.FAILED;
        VerificationFinding finding = new VerificationFinding(
                VerificationCheckType.CORRECTNESS,
                passed ? VerificationFindingStatus.PASS : VerificationFindingStatus.FAIL,
                passed ? VerificationSeverity.WARNING : VerificationSeverity.ERROR,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                passed ? "fake passed" : "fake failed", null);
        return new ProblemVerificationReport(request.verificationRequestId(), request.scope(), status,
                List.of(finding));
    }
}
