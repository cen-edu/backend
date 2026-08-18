package com.cenedu.backend.domain.problem.authoring.verification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProblemVerificationBundleTest {

    @Test
    @DisplayName("내용이 통과해도 자산이 실패하면 최종 검증은 FAILED다")
    void assetFailureMakesWholeVerificationFail() {
        UUID rootRequestId = UUID.randomUUID();
        ProblemVerificationReport content = report(
                VerificationScope.CONTENT, VerificationOverallStatus.PASSED);
        ProblemVerificationReport asset = report(
                VerificationScope.ASSET, VerificationOverallStatus.FAILED);

        ProblemVerificationBundle bundle = ProblemVerificationBundle.merge(
                rootRequestId, content, asset);

        assertThat(bundle.verificationRequestId()).isEqualTo(rootRequestId);
        assertThat(bundle.overallStatus()).isEqualTo(VerificationOverallStatus.FAILED);
    }

    @Test
    @DisplayName("두 검증 중 하나라도 처리 오류면 재검증 가능한 ERROR로 병합한다")
    void technicalErrorTakesPriority() {
        ProblemVerificationBundle bundle = ProblemVerificationBundle.merge(
                UUID.randomUUID(),
                report(VerificationScope.CONTENT, VerificationOverallStatus.FAILED),
                report(VerificationScope.ASSET, VerificationOverallStatus.ERROR));

        assertThat(bundle.overallStatus()).isEqualTo(VerificationOverallStatus.ERROR);
    }

    private ProblemVerificationReport report(
            VerificationScope scope,
            VerificationOverallStatus status
    ) {
        return new ProblemVerificationReport(
                UUID.randomUUID(), scope, status, List.of());
    }
}
