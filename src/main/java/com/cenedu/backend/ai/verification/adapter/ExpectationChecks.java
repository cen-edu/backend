package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumContext;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotMetadata;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationExpectation;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.entity.enums.DiagnosticType;
import com.cenedu.backend.global.common.enums.EvaluationArea;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.springframework.stereotype.Component;

/**
 * 후보가 요청받은 조건대로 만들어졌는지 값으로 대조한다. LLM 을 부르지 않는다.
 *
 * <p>기대치가 주어지지 않은 항목은 {@code NOT_APPLICABLE} 이다. 기대치 없이 PASS 를 내면
 * "조건을 확인했다"와 "조건이 없었다"가 같은 값으로 남는다.
 *
 * <p>각 항목을 독립 메서드로 둔다. 하나가 예외를 던져도 나머지 Finding 이 나와야 하며,
 * 그 격리는 {@link ProblemVerificationAdapter} 가 메서드 단위로 한다.
 */
@Component
public class ExpectationChecks {

    /** 교육과정 범위 이탈은 교사에게 나가면 안 된다. 심각도는 {@link Findings} 표에서 ERROR 다. */
    public VerificationFinding curriculumAlignment(
            QuestionSnapshotV1 snapshot, VerificationExpectation expectation
    ) {
        CurriculumContext expected = expectation == null ? null : expectation.expectedCurriculum();
        if (expected == null || expected.subUnitId() == null) {
            return Findings.notApplicable(VerificationCheckType.CURRICULUM_ALIGNMENT,
                    "기대 교육과정이 주어지지 않았습니다.");
        }
        Long actual = metadata(snapshot) == null ? null : metadata(snapshot).subUnitId();
        if (expected.subUnitId().equals(actual)) {
            return Findings.pass(VerificationCheckType.CURRICULUM_ALIGNMENT,
                    "소단원이 기대 교육과정과 일치합니다.");
        }
        return Findings.fail(
                VerificationCheckType.CURRICULUM_ALIGNMENT,
                VerificationIssueCode.CURRICULUM_MISMATCH,
                "문항의 소단원이 기대 교육과정과 다릅니다.",
                "expected=" + expected.subUnitId() + ", actual=" + actual);
    }

    public VerificationFinding difficulty(
            QuestionSnapshotV1 snapshot, VerificationExpectation expectation
    ) {
        String expected = expectation == null ? null : expectation.expectedDifficulty();
        if (expected == null || expected.isBlank()) {
            return Findings.notApplicable(VerificationCheckType.DIFFICULTY,
                    "기대 난이도가 주어지지 않았습니다.");
        }
        String actual = metadata(snapshot) == null ? null : metadata(snapshot).difficulty();
        if (expected.equalsIgnoreCase(actual)) {
            return Findings.pass(VerificationCheckType.DIFFICULTY, "난이도가 기대치와 일치합니다.");
        }
        return Findings.fail(
                VerificationCheckType.DIFFICULTY,
                VerificationIssueCode.DIFFICULTY_MISMATCH,
                "문항의 난이도가 기대치와 다릅니다.",
                "expected=" + expected + ", actual=" + actual);
    }

    public VerificationFinding evaluationArea(
            QuestionSnapshotV1 snapshot, VerificationExpectation expectation
    ) {
        EvaluationArea expected = expectation == null ? null : expectation.targetEvaluationArea();
        if (expected == null) {
            return Findings.notApplicable(VerificationCheckType.EVALUATION_AREA,
                    "목표 평가 영역이 주어지지 않았습니다.");
        }
        EvaluationArea actual = metadata(snapshot) == null
                ? null : metadata(snapshot).evaluationArea();
        if (expected == actual) {
            return Findings.pass(VerificationCheckType.EVALUATION_AREA,
                    "평가 영역이 목표와 일치합니다.");
        }
        return Findings.fail(
                VerificationCheckType.EVALUATION_AREA,
                VerificationIssueCode.EVALUATION_AREA_MISMATCH,
                "문항의 평가 영역이 목표와 다릅니다.",
                "expected=" + expected + ", actual=" + actual);
    }

    /**
     * 목표 진단 유형이 실제 칸들의 진단 유형을 모두 포함하는지 본다.
     *
     * <p><b>STEP_FILL 외에는 {@code NOT_APPLICABLE} 이다.</b> Validator 가 STEP_FILL 이 아닌
     * 문항의 {@code diagnosticType} 을 {@code null} 로 강제하므로, 다른 유형에서 이 검사를 돌리면
     * 빈 집합을 비교해 <b>항상 PASS</b> 가 된다. 그건 검사한 것이 아니라 검사가 없는 것이다.
     */
    public VerificationFinding diagnosticType(
            QuestionSnapshotV1 snapshot, VerificationExpectation expectation
    ) {
        QuestionType questionType = metadata(snapshot) == null
                ? null : metadata(snapshot).questionType();
        if (questionType != QuestionType.STEP_FILL) {
            return Findings.notApplicable(VerificationCheckType.DIAGNOSTIC_TYPE,
                    "진단 유형은 STEP_FILL 에서만 사용합니다.");
        }
        List<DiagnosticType> targets = expectation == null ? null : expectation.targetDiagnosticTypes();
        if (targets == null || targets.isEmpty()) {
            return Findings.notApplicable(VerificationCheckType.DIAGNOSTIC_TYPE,
                    "목표 진단 유형이 주어지지 않았습니다.");
        }

        Set<DiagnosticType> allowed = EnumSet.copyOf(targets);
        List<String> outside = new ArrayList<>();
        for (SnapshotAnswerUnit unit : snapshot.answerUnits()) {
            if (unit == null || unit.diagnosticType() == null) {
                continue;
            }
            if (!allowed.contains(unit.diagnosticType())) {
                outside.add(unit.unitKey() + "=" + unit.diagnosticType());
            }
        }
        if (outside.isEmpty()) {
            return Findings.pass(VerificationCheckType.DIAGNOSTIC_TYPE,
                    "모든 칸의 진단 유형이 목표 범위 안에 있습니다.");
        }
        return Findings.fail(
                VerificationCheckType.DIAGNOSTIC_TYPE,
                VerificationIssueCode.DIAGNOSTIC_TYPE_MISMATCH,
                "목표 범위에 없는 진단 유형이 있습니다.",
                "target=" + allowed + ", outside=" + String.join(", ", outside));
    }

    private static SnapshotMetadata metadata(QuestionSnapshotV1 snapshot) {
        return snapshot == null ? null : snapshot.metadata();
    }
}
