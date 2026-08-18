package com.cenedu.backend.ai.verification.adapter;

import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.springframework.stereotype.Component;

/**
 * 서술형 채점 기준의 <b>의미</b>를 심사한다. ESSAY 외에는 대상이 아니다.
 *
 * <p>구조 검사는 하지 않는다 — 항목 수 2~5 와 {@code weightPercent} 합 100 은
 * {@code SnapshotStructuralValidator} 가 이미 본다. 두 곳에서 같은 것을 재면 위반 하나가 Finding
 * 두 개로 나가고, 조율측이 두 배로 재시도한다.
 *
 * <p>코드는 {@code RUBRIC_INVALID} 하나다. 어느 축인지는 <b>{@code evidence} 첫머리에 대문자로</b>
 * 적는다. 이렇게 두면 조율측이 나중에 축별로 분기하고 싶어질 때 파싱만 붙이면 되고, 그때까지는
 * 계약 enum 을 늘리지 않아도 된다.
 */
@Component
public class RubricQualityChecker {

    /** 프롬프트가 낼 수 있는 축. 모델이 다른 문자열을 내면 형식 위반으로 본다. */
    private static final Set<String> AXES =
            Set.of("OUT_OF_SCOPE", "UNCOVERED", "OVERLAPPING", "UNJUDGEABLE");

    private final VerificationLlmClient llmClient;

    public RubricQualityChecker(VerificationLlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public VerificationFinding check(QuestionSnapshotV1 snapshot) {
        QuestionType questionType = snapshot.metadata() == null
                ? null : snapshot.metadata().questionType();
        if (questionType != QuestionType.ESSAY) {
            return Findings.notApplicable(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준은 서술형에서만 사용합니다.");
        }

        VerificationLlmClient.RubricJudgement judgement = llmClient.judgeRubric(snapshot);
        if (!judgement.hasIssue()) {
            return Findings.pass(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준이 문항 범위 안에서 서로 겹치지 않고 판정 가능합니다.");
        }

        String axis = judgement.axis().toUpperCase();
        if (!AXES.contains(axis)) {
            // 축을 알 수 없으면 무엇이 문제인지 조율측에 전달할 수 없다. FAIL 로 내리지 않는다.
            return Findings.error(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준 심사 응답의 축을 알 수 없습니다.", "axis=" + judgement.axis());
        }
        return Findings.fail(
                VerificationCheckType.RUBRIC_QUALITY,
                VerificationIssueCode.RUBRIC_INVALID,
                "채점 기준에 의미 결함이 있습니다.",
                axis + ": " + judgement.detail());
    }
}
