package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.asset.DraftAssetManifest;
import com.cenedu.backend.domain.problem.authoring.candidate.ProblemCandidateDraft;
import com.cenedu.backend.domain.problem.authoring.semantic.materialization.SemanticMaterializationReport;

/** Problem 조율측이 검증 Adapter에 넘기는 후보·기대치·맥락의 전체 계약이다. */
public record ProblemVerificationRequest(
        UUID verificationRequestId,
        VerificationScope scope,
        VerificationOperationType operationType,
        ProblemCandidateDraft candidate,
        DraftAssetManifest assetManifest,
        VerificationExpectation expectation,
        VerificationContext context,
        SemanticMaterializationReport semanticReport,
        VerificationProfile profile
) {
    public ProblemVerificationRequest {
        profile = profile == null ? VerificationProfile.FULL_CONTENT : profile;
    }

    public ProblemVerificationRequest(UUID verificationRequestId, VerificationScope scope,
            VerificationOperationType operationType, ProblemCandidateDraft candidate,
            DraftAssetManifest assetManifest, VerificationExpectation expectation,
            VerificationContext context, SemanticMaterializationReport semanticReport) {
        this(verificationRequestId, scope, operationType, candidate, assetManifest,
                expectation, context, semanticReport, VerificationProfile.FULL_CONTENT);
    }

    public ProblemVerificationRequest(UUID verificationRequestId, VerificationScope scope,
            VerificationOperationType operationType, ProblemCandidateDraft candidate,
            DraftAssetManifest assetManifest, VerificationExpectation expectation,
            VerificationContext context) {
        this(verificationRequestId, scope, operationType, candidate, assetManifest,
                expectation, context, null, VerificationProfile.FULL_CONTENT);
    }
}
