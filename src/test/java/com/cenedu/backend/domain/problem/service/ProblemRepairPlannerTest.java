package com.cenedu.backend.domain.problem.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationScope;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

class ProblemRepairPlannerTest {
    private final ProblemRepairPlanner planner = new ProblemRepairPlanner();

    @Test
    void 여러_내용_오류를_한번의_묶음_수정대상으로_계획한다() {
        ProblemVerificationBundle bundle = bundle(
                finding(VerificationIssueCode.ANSWER_INCONSISTENT),
                finding(VerificationIssueCode.RUBRIC_INVALID));

        var plan = planner.plan(bundle);

        assertThat(plan.repairable()).isTrue();
        assertThat(plan.targets()).containsExactlyInAnyOrder(
                RepairTarget.EXPLANATION, RepairTarget.STEPS, RepairTarget.RUBRIC);
    }

    @Test
    void 오류_또는_판정불가가_섞이면_자동수정하지_않는다() {
        VerificationFinding error = new VerificationFinding(
                VerificationCheckType.CORRECTNESS, VerificationFindingStatus.ERROR,
                VerificationSeverity.ERROR, VerificationIssueCode.PROVIDER_ERROR,
                "공급자 오류", null);

        assertThat(planner.plan(bundle(error)).repairable()).isFalse();
    }

    private ProblemVerificationBundle bundle(VerificationFinding... findings) {
        UUID id = UUID.randomUUID();
        return ProblemVerificationBundle.contentOnly(id,
                new ProblemVerificationReport(id, VerificationScope.CONTENT,
                        VerificationOverallStatus.FAILED, List.of(findings)));
    }

    private VerificationFinding finding(VerificationIssueCode code) {
        return new VerificationFinding(VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationFindingStatus.FAIL, VerificationSeverity.ERROR, code,
                code.name(), null);
    }
}
