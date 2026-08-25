package com.cenedu.backend.domain.problem.authoring.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

class CandidateProcessingResultTest {

    @Test
    void 본문은_통과하고_이미지_내용만_실패했을때_환류한다() {
        CandidateProcessingResult result = resultWithAssetFailure(
                VerificationIssueCode.ASSET_IMAGE_REGENERATABLE);

        assertThat(result.imageRevisionRecommended()).isTrue();
    }

    @Test
    void manifest_무결성_실패는_이미지를_다시_그리지_않는다() {
        CandidateProcessingResult result = resultWithAssetFailure(
                VerificationIssueCode.ASSET_INCONSISTENT);

        assertThat(result.imageRevisionRecommended()).isFalse();
    }

    private CandidateProcessingResult resultWithAssetFailure(VerificationIssueCode issueCode) {
        UUID requestId = UUID.randomUUID();
        ProblemVerificationReport content = new ProblemVerificationReport(requestId,
                VerificationScope.CONTENT, VerificationOverallStatus.PASSED, List.of());
        VerificationFinding failure = new VerificationFinding(VerificationCheckType.ASSET_CONSISTENCY,
                VerificationFindingStatus.FAIL, VerificationSeverity.ERROR, issueCode,
                "자산 검증 실패", null);
        ProblemVerificationReport asset = new ProblemVerificationReport(requestId,
                VerificationScope.ASSET, VerificationOverallStatus.FAILED, List.of(failure));
        ProblemVerificationBundle bundle = ProblemVerificationBundle.merge(requestId, content, asset);
        return new CandidateProcessingResult(1L, 1, requestId,
                VerificationOverallStatus.FAILED, bundle, false);
    }
}
