package com.cenedu.backend.domain.problem.authoring.verification;

import java.util.UUID;

/** Version.verification_report JSON에 CONTENT·ASSET 검증을 하나의 최종 판정으로 보존한다. */
public record ProblemVerificationBundle(
        int schemaVersion,
        UUID verificationRequestId,
        ProblemVerificationReport contentReport,
        ProblemVerificationReport assetReport,
        VerificationOverallStatus overallStatus
) {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    /** 자산이 없는 문항의 CONTENT 검증만으로 최종 판정을 만든다. */
    public static ProblemVerificationBundle contentOnly(
            UUID verificationRequestId,
            ProblemVerificationReport contentReport
    ) {
        requireScope(contentReport, VerificationScope.CONTENT, "contentReport");
        return new ProblemVerificationBundle(
                CURRENT_SCHEMA_VERSION,
                verificationRequestId,
                contentReport,
                null,
                contentReport.overallStatus());
    }

    /** 자산이 있는 문항의 CONTENT·ASSET 검증을 모두 반영한다. */
    public static ProblemVerificationBundle merge(
            UUID verificationRequestId,
            ProblemVerificationReport contentReport,
            ProblemVerificationReport assetReport
    ) {
        requireScope(contentReport, VerificationScope.CONTENT, "contentReport");
        requireScope(assetReport, VerificationScope.ASSET, "assetReport");
        return new ProblemVerificationBundle(
                CURRENT_SCHEMA_VERSION,
                verificationRequestId,
                contentReport,
                assetReport,
                mergeStatus(contentReport.overallStatus(), assetReport.overallStatus()));
    }

    private static VerificationOverallStatus mergeStatus(
            VerificationOverallStatus contentStatus,
            VerificationOverallStatus assetStatus
    ) {
        if (contentStatus == VerificationOverallStatus.ERROR
                || assetStatus == VerificationOverallStatus.ERROR) {
            return VerificationOverallStatus.ERROR;
        }
        if (contentStatus == VerificationOverallStatus.FAILED
                || assetStatus == VerificationOverallStatus.FAILED) {
            return VerificationOverallStatus.FAILED;
        }
        return VerificationOverallStatus.PASSED;
    }

    private static void requireScope(ProblemVerificationReport report,
                                     VerificationScope expectedScope,
                                     String fieldName) {
        if (report == null || report.scope() != expectedScope) {
            throw new IllegalArgumentException(
                    fieldName + "의 scope은 " + expectedScope + "이어야 합니다.");
        }
    }
}
