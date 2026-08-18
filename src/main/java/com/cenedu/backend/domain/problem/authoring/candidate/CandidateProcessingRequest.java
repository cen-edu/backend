package com.cenedu.backend.domain.problem.authoring.candidate;

import com.cenedu.backend.domain.problem.authoring.verification.VerificationContext;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOperationType;
import com.cenedu.backend.domain.problem.entity.enums.AuthoringOperationType;

/** 후보를 Version으로 저장하고 검증하는 공통 조율 입력이다. */
public record CandidateProcessingRequest(
        long ownerTeacherId,
        long sessionId,
        Long parentVersionId,
        AuthoringOperationType operationType,
        VerificationOperationType verificationOperationType,
        ProblemCandidateDraft candidate,
        VerificationExpectation expectation,
        VerificationContext verificationContext,
        String changeSummary
) {
}
