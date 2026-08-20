package com.cenedu.backend.domain.problem.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.cenedu.backend.domain.problem.authoring.repair.ProblemRepairPlan;
import com.cenedu.backend.domain.problem.authoring.repair.RepairTarget;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationBundle;
import com.cenedu.backend.domain.problem.authoring.verification.ProblemVerificationReport;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

/** 검증 결과만으로 수정 대상을 결정해 추가 판정 LLM 호출을 막는다. */
@Component
public class ProblemRepairPlanner {

    /** FAILED Finding을 하나의 묶음 Repair 계획으로 변환한다. */
    public ProblemRepairPlan plan(ProblemVerificationBundle bundle) {
        if (bundle == null || bundle.overallStatus() != com.cenedu.backend.domain.problem.authoring.verification.VerificationOverallStatus.FAILED) {
            return ProblemRepairPlan.notRepairable("검증 실패가 아니므로 부분 수정하지 않습니다.");
        }
        List<VerificationFinding> findings = allFindings(bundle);
        if (findings.stream().anyMatch(finding -> finding.status() == VerificationFindingStatus.ERROR
                || finding.code() == VerificationIssueCode.UNVERIFIABLE)) {
            return ProblemRepairPlan.notRepairable("검증 결과가 불확실해 자동 수정하지 않습니다.");
        }
        EnumSet<RepairTarget> targets = EnumSet.noneOf(RepairTarget.class);
        for (VerificationFinding finding : findings) {
            if (finding.status() != VerificationFindingStatus.FAIL) continue;
            switch (finding.code()) {
                // Solver 불일치만으로 어느 쪽이 틀렸는지 단정할 수 없다. 원본 검사 합의 신호가
                // 추가되기 전에는 정답을 자동 수정하지 않는다.
                case ANSWER_INCORRECT -> {
                    return ProblemRepairPlan.notRepairable("독립 검증 신호가 합의되지 않아 정답을 자동 수정하지 않습니다.");
                }
                case ANSWER_INCONSISTENT -> {
                    targets.add(RepairTarget.EXPLANATION);
                    targets.add(RepairTarget.STEPS);
                }
                case RUBRIC_INVALID -> targets.add(RepairTarget.RUBRIC);
                case ASSET_INCONSISTENT -> targets.add(RepairTarget.ASSET);
                default -> { return ProblemRepairPlan.notRepairable("부분 수정으로 복구할 수 없는 오류입니다."); }
            }
        }
        return targets.isEmpty()
                ? ProblemRepairPlan.notRepairable("수정 가능한 오류가 없습니다.")
                : new ProblemRepairPlan(targets, findings.stream().map(VerificationFinding::message).toList(), true);
    }

    private List<VerificationFinding> allFindings(ProblemVerificationBundle bundle) {
        return java.util.stream.Stream.of(bundle.contentReport(), bundle.assetReport())
                .filter(java.util.Objects::nonNull)
                .flatMap(report -> report.findings().stream())
                .toList();
    }
}
