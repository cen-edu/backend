package com.cenedu.backend.domain.problem.authoring.candidate;

import java.util.UUID;

import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

/** 후보 Version의 번호·검증·승격 결과를 Worker에 반환한다. */
public record CandidateProcessingResult(
        Long versionId,
        int versionNo,
        UUID verificationRequestId,
        VerificationOverallStatus status,
        ProblemVerificationBundle verificationBundle,
        boolean promoted
) {
    /** 본문은 통과했고 이미지 내용/설명만 다시 만들 수 있는 실패인지 판정한다. */
    public boolean imageRevisionRecommended() {
        if (status != VerificationOverallStatus.FAILED || verificationBundle == null
                || verificationBundle.contentReport() == null
                || verificationBundle.contentReport().overallStatus() != VerificationOverallStatus.PASSED
                || verificationBundle.assetReport() == null
                || verificationBundle.assetReport().overallStatus() != VerificationOverallStatus.FAILED
                || verificationBundle.assetReport().findings() == null) {
            return false;
        }
        var failures = verificationBundle.assetReport().findings().stream()
                .filter(finding -> finding.status() == VerificationFindingStatus.FAIL
                        || finding.status() == VerificationFindingStatus.ERROR)
                .toList();
        return !failures.isEmpty() && failures.stream().allMatch(finding ->
                finding.status() == VerificationFindingStatus.FAIL
                        && finding.code() == VerificationIssueCode.ASSET_IMAGE_REGENERATABLE);
    }
}
