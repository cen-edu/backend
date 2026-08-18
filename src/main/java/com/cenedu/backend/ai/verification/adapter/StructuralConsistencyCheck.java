package com.cenedu.backend.ai.verification.adapter;

import java.util.List;

import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.validation.SnapshotStructuralValidator;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;

import org.springframework.stereotype.Component;

/**
 * {@code ANSWER_CONSISTENCY} 를 저작측 Validator 에 위임한다.
 *
 * <p><b>같은 규칙을 두 번 구현하지 않는다.</b> 요청서가 이 항목에 배정한 검사(유형별 answerUnit 구성,
 * {@code unitKey}↔{@code stepKey}↔{@code segments} 참조 정합)는
 * {@link SnapshotStructuralValidator} 가 이미 전부 한다 —
 * {@code validateStepReferences} 가 BLANK 중복·ANSWER_REF 선행 참조·BLANK↔answerUnit 양방향 정합과
 * {@code stepKey} 일치를, {@code validateAnswerUnits} 가 compareMethod 별 null 규칙과 키·순서를,
 * 유형별 메서드가 허용 조합을 본다. 여기서 다시 짜면 두 구현이 갈라지는 순간 어느 쪽이 정답인지
 * 알 수 없게 된다.
 *
 * <p>대신 <b>검사 범위가 이 CheckType 의 이름보다 넓다.</b> Validator 는 해설·개념 안내·표 markup
 * 안전성까지 본다. 좁히려면 위반 문자열을 접두사로 걸러야 하는데, 그건 Validator 의 메시지 형식에
 * 의존하는 코드가 된다. 넓게 두고 근거에 전체 위반을 적는 편을 골랐다. 범위 조정은 조율측 판단이다.
 */
@Component
public class StructuralConsistencyCheck {

    /** 근거에 담을 위반 개수 상한. 전부 담으면 한 Finding 이 수십 줄이 된다. */
    private static final int MAX_EVIDENCE_ITEMS = 5;

    private final SnapshotStructuralValidator structuralValidator;

    public StructuralConsistencyCheck(SnapshotStructuralValidator structuralValidator) {
        this.structuralValidator = structuralValidator;
    }

    public VerificationFinding check(QuestionSnapshotV1 snapshot) {
        List<String> violations = structuralValidator.violations(snapshot);
        if (violations.isEmpty()) {
            return Findings.pass(VerificationCheckType.ANSWER_CONSISTENCY,
                    "답안 단위 구성과 논리 키 참조가 유형별 불변식을 만족합니다.");
        }

        String evidence = violations.stream().limit(MAX_EVIDENCE_ITEMS)
                .reduce((left, right) -> left + " | " + right)
                .orElse("");
        if (violations.size() > MAX_EVIDENCE_ITEMS) {
            evidence += " | (외 " + (violations.size() - MAX_EVIDENCE_ITEMS) + "건)";
        }
        return Findings.fail(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                "구조·참조 정합 위반이 " + violations.size() + "건 있습니다.",
                evidence);
    }
}
