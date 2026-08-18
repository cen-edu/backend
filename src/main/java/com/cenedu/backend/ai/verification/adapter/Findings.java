package com.cenedu.backend.ai.verification.adapter;

import java.util.Map;

import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFindingStatus;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;

/**
 * Finding 을 만드는 한 곳. <b>심각도 표를 여기 하나만 둔다.</b>
 *
 * <p>각 판정기가 자기 심각도를 직접 정하면, 같은 항목이 경로마다 다른 심각도로 나가고
 * {@code overallStatus} 가 호출 경로에 따라 달라진다. 그 차이는 로그에도 남지 않는다.
 *
 * <p>{@code WARNING} 은 {@code overallStatus} 를 {@code FAILED} 로 만들지 않는다. 난이도·평가영역이
 * 기대와 다른 것은 교사가 보고 판단할 일이고, 정답이 틀린 것은 교사에게 나가면 안 되는 일이다.
 */
final class Findings {

    /**
     * 심각도 표.
     *
     * <p>{@code CURRICULUM_ALIGNMENT} 를 ERROR 로 둔다 — 교육과정 범위를 벗어난 문항이 교사에게
     * 나가면 안 된다. 난이도가 한 칸 다른 것과는 성격이 다르다.
     *
     * <p>{@code ASSET_CONSISTENCY} 도 ERROR 로 둔다. altText 에 정답이 새면 학생이 그림 설명만
     * 읽고 답을 얻고, manifest 가 준비되지 않은 문항은 애초에 승격할 수 없다.
     */
    private static final Map<VerificationCheckType, VerificationSeverity> SEVERITY = Map.of(
            VerificationCheckType.CORRECTNESS, VerificationSeverity.ERROR,
            VerificationCheckType.ANSWER_CONSISTENCY, VerificationSeverity.ERROR,
            VerificationCheckType.CURRICULUM_ALIGNMENT, VerificationSeverity.ERROR,
            VerificationCheckType.RUBRIC_QUALITY, VerificationSeverity.ERROR,
            VerificationCheckType.EDIT_REQUIREMENT, VerificationSeverity.ERROR,
            VerificationCheckType.PROTECTED_SCOPE, VerificationSeverity.ERROR,
            VerificationCheckType.ASSET_CONSISTENCY, VerificationSeverity.ERROR,
            VerificationCheckType.DIFFICULTY, VerificationSeverity.WARNING,
            VerificationCheckType.EVALUATION_AREA, VerificationSeverity.WARNING,
            VerificationCheckType.DIAGNOSTIC_TYPE, VerificationSeverity.WARNING);

    private Findings() {
    }

    static VerificationSeverity severityOf(VerificationCheckType checkType) {
        VerificationSeverity severity = SEVERITY.get(checkType);
        if (severity == null) {
            // enum 에 값이 추가됐는데 표를 안 고친 경우다. 조용히 WARNING 으로 두면
            // 새 항목이 무엇을 막아야 하는지 아무도 정하지 않은 상태로 통과한다.
            throw new IllegalStateException(
                    "심각도가 정해지지 않은 CheckType 입니다: " + checkType);
        }
        return severity;
    }

    static VerificationFinding pass(VerificationCheckType checkType, String message) {
        return new VerificationFinding(
                checkType, VerificationFindingStatus.PASS, severityOf(checkType), null, message, null);
    }

    static VerificationFinding fail(
            VerificationCheckType checkType,
            VerificationIssueCode code,
            String message,
            String evidence
    ) {
        return new VerificationFinding(
                checkType, VerificationFindingStatus.FAIL, severityOf(checkType), code, message, evidence);
    }

    /**
     * 심각도를 명시해서 낸다. <b>표를 우회하는 유일한 자리다.</b>
     *
     * <p>한 CheckType 안에서 항목별로 심각도가 갈리는 경우가 있다 — 개념 안내가 정답 값을 담으면
     * 학생이 화면만 보고 답을 얻으므로 {@code ERROR} 지만, 풀이 방향만 지정한 것은 교사가 보고
     * 판단할 문제라 {@code WARNING} 이다. 계약의 CheckType 이 10종뿐이라 둘이 같은 칸에 들어간다.
     *
     * <p>{@link #SEVERITY} 표는 그대로 <b>기본값</b>으로 남는다. 이 오버로드를 쓰는 곳이 늘어나면
     * 표가 의미를 잃으므로, 늘리기 전에 CheckType 추가를 계약 소유자와 논의한다.
     */
    static VerificationFinding fail(
            VerificationCheckType checkType,
            VerificationIssueCode code,
            String message,
            String evidence,
            VerificationSeverity severity
    ) {
        return new VerificationFinding(
                checkType, VerificationFindingStatus.FAIL, severity, code, message, evidence);
    }

    /**
     * 검사 대상이 아닐 때. <b>Finding 을 생략하지 않는다</b> — 검사했으나 대상이 아닌 것과
     * 검사하지 않은 것은 다르고, 생략하면 조율측이 둘을 구분할 수 없다.
     */
    static VerificationFinding notApplicable(VerificationCheckType checkType, String message) {
        return new VerificationFinding(
                checkType, VerificationFindingStatus.NOT_APPLICABLE, severityOf(checkType),
                null, message, null);
    }

    /**
     * 판정을 시도했으나 처리에 실패했을 때. Provider 장애·응답 형식 위반·판정기 예외가 여기 온다.
     *
     * <p>{@code FAIL} 로 내리지 않는다. 모델이 형식을 어긴 것과 문항이 틀린 것은 다르다.
     */
    static VerificationFinding error(VerificationCheckType checkType, String message, String evidence) {
        return new VerificationFinding(
                checkType, VerificationFindingStatus.ERROR, severityOf(checkType),
                VerificationIssueCode.PROVIDER_ERROR, message, evidence);
    }
}
