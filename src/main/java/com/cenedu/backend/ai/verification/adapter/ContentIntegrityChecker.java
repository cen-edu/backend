package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.cenedu.backend.domain.problem.authoring.generation.CurriculumScope;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationSeverity;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.springframework.stereotype.Component;

/**
 * 정답이 든 <b>원본</b>으로 판정하는 검사를 모두 맡는다.
 *
 * <p>Blind 경로로는 판정할 수 없는 항목들이다 — {@code explanation} 과 {@code learningGuide} 는
 * Blind 에서 제거되므로 Solver 가 볼 수 없다.
 *
 * <p><b>루브릭 심사를 흡수했다.</b> 별도 {@code RubricQualityChecker} 를 두지 않은 이유는, 호출이
 * 하나면 응답도 하나이고 그 응답을 두 컴포넌트가 나눠 읽으면 "누가 호출하는가"가 흐려지기 때문이다.
 * 토글이 꺼졌을 때 ESSAY 만 루브릭 전용 호출을 다시 하는 판단도 한 곳에 있어야 한다.
 *
 * <p>LLM 호출 횟수 (문항 하나, CONTENT 범위):
 * <pre>
 * 토글 on   비-ESSAY 1회(원본검사)      ESSAY 1회(원본검사에 루브릭 포함)
 * 토글 off  비-ESSAY 0회                ESSAY 1회(루브릭 전용)
 * </pre>
 * 여기에 Solver 호출(비-ESSAY 1회, ESSAY 0회)이 더해져 합계가 지시서의 2회 상한을 지킨다.
 * <b>토글을 꺼도 ESSAY 루브릭은 돈다</b> — 루브릭은 토글의 대상이 아니다.
 */
@Component
public class ContentIntegrityChecker {

    /** 루브릭 4축. 모델이 다른 문자열을 내면 형식 위반으로 본다. */
    private static final Set<String> RUBRIC_AXES = Set.of(
            EvidencePrefix.OUT_OF_SCOPE, EvidencePrefix.UNCOVERED,
            EvidencePrefix.OVERLAPPING, EvidencePrefix.UNJUDGEABLE);

    private final VerificationLlmClient llmClient;
    private final ContentCheckProperties properties;

    public ContentIntegrityChecker(
            VerificationLlmClient llmClient, ContentCheckProperties properties
    ) {
        this.llmClient = llmClient;
        this.properties = properties;
    }

    /**
     * @return 이 검사가 내는 Finding 전부. {@code RUBRIC_QUALITY} 는 항상 1건 들어 있고,
     *         {@code ANSWER_CONSISTENCY} 는 결함이 있을 때만 들어 있다
     */
    public List<VerificationFinding> check(
            QuestionSnapshotV1 snapshot,
            CurriculumScope expectedCurriculum
    ) {
        boolean essay = isEssay(snapshot);

        if (!properties.enabled()) {
            // 해설·누출 Finding 을 만들지 않는다. PASS 로도 내지 않는다 — 검사하지 않았다.
            return List.of(essay ? rubricOnly(snapshot) : rubricNotApplicable());
        }

        List<OriginalDefect> defects = llmClient.inspectOriginal(
                snapshot, essay, expectedCurriculum);

        List<VerificationFinding> findings = new ArrayList<>();
        for (OriginalDefect defect : defects) {
            switch (defect.type()) {
                case OriginalDefect.TYPE_EXPLANATION -> findings.add(explanationDefect(defect));
                case OriginalDefect.TYPE_LEAKAGE -> findings.add(leakageDefect(defect));
                // 구조 불변식은 코드가 판정한다. 모델이 STRUCTURE 로 분류한 것도 버리지 않고
                // 같은 접두어로 낸다 — 판정을 임의로 좁히면 무엇을 봤는지 알 수 없게 된다.
                case OriginalDefect.TYPE_STRUCTURE -> findings.add(structureDefect(defect));
                case OriginalDefect.TYPE_CURRICULUM -> findings.add(curriculumDefect(defect));
                case OriginalDefect.TYPE_RUBRIC -> {
                    // 루브릭은 아래에서 한 건으로 접는다.
                }
                default -> findings.add(Findings.error(
                        VerificationCheckType.ANSWER_CONSISTENCY,
                        "원본 검사 응답의 결함 유형을 알 수 없습니다.", "type=" + defect.type()));
            }
        }
        findings.add(rubricFinding(essay, defects));
        return List.copyOf(findings);
    }

    /** 해설이 정답과 모순되거나 스냅샷에 없는 값을 쓴다. */
    private VerificationFinding explanationDefect(OriginalDefect defect) {
        return Findings.fail(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                "해설이 정답 단위와 맞지 않습니다.",
                EvidencePrefix.of(EvidencePrefix.EXPLANATION, defect.describe()));
    }

    /**
     * 개념 안내가 정답이나 풀이 방향을 노출한다.
     *
     * <p>심각도가 둘로 갈린다 — 정답 값을 그대로 담으면 학생이 화면만 보고 답을 얻으므로
     * {@code ERROR} 다. 풀이 방향만 지정한 것은 교사가 보고 판단할 문제이므로 {@code WARNING} 이고,
     * 이 경우 {@code overallStatus} 를 {@code FAILED} 로 만들지 않는다.
     *
     * <p>분류를 모르면 엄격한 쪽({@code ERROR})으로 떨어뜨린다. 모르는 값을 경고로 두면
     * 정답 노출이 경고로 흘러나갈 수 있다.
     */
    private VerificationFinding leakageDefect(OriginalDefect defect) {
        VerificationSeverity severity =
                OriginalDefect.KIND_SOLUTION_DIRECTION.equals(defect.kind())
                        ? VerificationSeverity.WARNING
                        : VerificationSeverity.ERROR;
        String message = severity == VerificationSeverity.WARNING
                ? "개념 안내가 풀이 방향을 지정합니다."
                : "개념 안내가 정답 값을 노출합니다.";
        return Findings.fail(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                message,
                EvidencePrefix.of(EvidencePrefix.LEAKAGE, defect.describe()),
                severity);
    }

    private VerificationFinding structureDefect(OriginalDefect defect) {
        return Findings.fail(
                VerificationCheckType.ANSWER_CONSISTENCY,
                VerificationIssueCode.ANSWER_INCONSISTENT,
                "원본 검사가 구조 결함을 지적했습니다.",
                EvidencePrefix.of(EvidencePrefix.STRUCTURE, defect.describe()));
    }

    /** 메타데이터 ID가 아니라 실제 발문에서 확인된 교육과정 범위 이탈이다. */
    private VerificationFinding curriculumDefect(OriginalDefect defect) {
        return Findings.fail(
                VerificationCheckType.CURRICULUM_ALIGNMENT,
                VerificationIssueCode.CURRICULUM_MISMATCH,
                "문항 내용이 요청한 소단원 범위와 다릅니다.",
                EvidencePrefix.of("CURRICULUM", defect.describe()));
    }

    /** 통합 응답에서 루브릭 절만 접어 한 건으로 만든다. */
    private VerificationFinding rubricFinding(boolean essay, List<OriginalDefect> defects) {
        if (!essay) {
            return rubricNotApplicable();
        }
        OriginalDefect rubricDefect = defects.stream()
                .filter(defect -> OriginalDefect.TYPE_RUBRIC.equals(defect.type()))
                .findFirst()
                .orElse(null);
        if (rubricDefect == null) {
            return Findings.pass(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준이 문항 범위 안에서 서로 겹치지 않고 판정 가능합니다.");
        }
        return rubricFail(rubricDefect.kind(), rubricDefect.describe());
    }

    /** 토글이 꺼진 ESSAY 경로. 루브릭만 보는 전용 호출이다. */
    private VerificationFinding rubricOnly(QuestionSnapshotV1 snapshot) {
        VerificationLlmClient.RubricJudgement judgement = llmClient.judgeRubric(snapshot);
        if (!judgement.hasIssue()) {
            return Findings.pass(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준이 문항 범위 안에서 서로 겹치지 않고 판정 가능합니다.");
        }
        return rubricFail(judgement.axis().toUpperCase(), judgement.detail());
    }

    private VerificationFinding rubricFail(String axis, String detail) {
        if (!RUBRIC_AXES.contains(axis)) {
            // 축을 알 수 없으면 무엇이 문제인지 조율측에 전달할 수 없다. FAIL 로 내리지 않는다.
            return Findings.error(VerificationCheckType.RUBRIC_QUALITY,
                    "채점 기준 심사 응답의 축을 알 수 없습니다.", "axis=" + axis);
        }
        return Findings.fail(
                VerificationCheckType.RUBRIC_QUALITY,
                VerificationIssueCode.RUBRIC_INVALID,
                "채점 기준에 의미 결함이 있습니다.",
                EvidencePrefix.of(axis, detail));
    }

    private VerificationFinding rubricNotApplicable() {
        return Findings.notApplicable(VerificationCheckType.RUBRIC_QUALITY,
                "채점 기준은 서술형에서만 사용합니다.");
    }

    private static boolean isEssay(QuestionSnapshotV1 snapshot) {
        return snapshot != null && snapshot.metadata() != null
                && snapshot.metadata().questionType() == QuestionType.ESSAY;
    }
}
