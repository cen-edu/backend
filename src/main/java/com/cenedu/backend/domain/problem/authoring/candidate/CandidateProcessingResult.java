package com.cenedu.backend.domain.problem.authoring.candidate;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;

/** 후보 Version의 번호·검증·승격 결과를 Worker에 반환한다. */
public record CandidateProcessingResult(
        Long versionId,
        int versionNo,
        UUID verificationRequestId,
        VerificationOverallStatus status,
        ProblemVerificationBundle verificationBundle,
        boolean promoted
) {
}
