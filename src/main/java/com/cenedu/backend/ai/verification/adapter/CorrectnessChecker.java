package com.cenedu.backend.ai.verification.adapter;

import java.util.ArrayList;
import java.util.List;

import com.cenedu.backend.domain.grading.service.AnswerNormalizer;
import com.cenedu.backend.domain.grading.service.RuleGrader;
import com.cenedu.backend.domain.problem.authoring.model.QuestionSnapshotV1;
import com.cenedu.backend.domain.problem.authoring.model.SnapshotAnswerUnit;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationCheckType;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationFinding;
import com.cenedu.backend.domain.problem.authoring.verification.VerificationIssueCode;
import com.cenedu.backend.global.common.enums.CompareMethod;
import com.cenedu.backend.global.common.enums.QuestionType;

import org.springframework.stereotype.Component;

/**
 * Solver 가 혼자 낸 답을 스냅샷의 정답과 대조한다.
 *
 * <p>{@code AnswerNormalizer}·{@code RuleGrader} 는 {@code domain.grading} 것을 그대로 쓴다.
 * 검증기가 자기 정규화기를 따로 가지면 채점과 검증이 서로 다른 기준으로 같은 답을 판정하게 되고,
 * 검증을 통과한 문항이 채점에서 틀리는 일이 생긴다.
 *
 * <p>정규형은 <b>항상 {@code answerRaw} 에서 직접 만든다.</b> {@code answerNormalized} 는 계약상
 * 서버 정규화기가 채우는 값이지만 검증 시점에 채워졌다는 보장이 없고(Validator 도 필수로 요구하지
 * 않는다), 객관식에서는 오히려 {@code null} 이 강제된다. 채점 쪽도 같은 이유로 정답에 정규화를
 * 한 번 더 먹인다.
 */
@Component
public class CorrectnessChecker {

    private final AnswerNormalizer answerNormalizer;
    private final RuleGrader ruleGrader;

    public CorrectnessChecker(AnswerNormalizer answerNormalizer, RuleGrader ruleGrader) {
        this.answerNormalizer = answerNormalizer;
        this.ruleGrader = ruleGrader;
    }

    /**
     * 이 문항을 판정하려면 Solver 호출이 필요한가.
     *
     * <p>서술형은 대조할 값이 없어 Solver 의 답을 쓸 데가 없다. 그래도 부르면 토큰을 쓰는 것을 넘어
     * <b>판정에 쓰이지도 않는 호출로 문항을 외부에 보내는 것</b>이 된다.
     */
    public boolean requiresSolver(QuestionSnapshotV1 snapshot) {
        QuestionType questionType = snapshot == null || snapshot.metadata() == null
                ? null : snapshot.metadata().questionType();
        return questionType != null && questionType != QuestionType.ESSAY;
    }

    public VerificationFinding check(QuestionSnapshotV1 snapshot, SolverAnswer solverAnswer) {
        QuestionType questionType = snapshot.metadata() == null
                ? null : snapshot.metadata().questionType();
        if (questionType == null) {
            return Findings.error(VerificationCheckType.CORRECTNESS,
                    "문항 유형이 없어 정확성을 판정할 수 없습니다.", null);
        }
        if (questionType == QuestionType.ESSAY) {
            return essay();
        }
        if (!solverAnswer.solved()) {
            return unverifiable(solverAnswer.reason());
        }

        List<String> requiredKeys = snapshot.answerUnits().stream()
                .filter(unit -> unit != null)
                .map(SnapshotAnswerUnit::unitKey)
                .toList();
        List<String> missing = solverAnswer.missingKeys(requiredKeys);
        if (!missing.isEmpty()) {
            return unverifiable("답하지 않은 칸: " + String.join(", ", missing));
        }

        return switch (questionType) {
            case MULTIPLE_CHOICE -> multipleChoice(snapshot, solverAnswer);
            case SHORT_INPUT, STEP_FILL -> compareByUnit(snapshot, solverAnswer);
            // ESSAY 는 위에서 이미 돌려보냈다.
            case ESSAY -> essay();
        };
    }

    /** 서술형은 값 일치 검사가 아니라 별도 루브릭 품질·해설 정합 검사로 검증한다. */
    private VerificationFinding essay() {
        return Findings.notApplicable(
                VerificationCheckType.CORRECTNESS,
                "서술형은 단일 정답 값 대조 대신 루브릭 품질과 해설 정합성으로 검증합니다.");
    }

    private VerificationFinding unverifiable(String reason) {
        return Findings.fail(
                VerificationCheckType.CORRECTNESS,
                VerificationIssueCode.UNVERIFIABLE,
                "검증기가 문항을 풀지 못해 정확성을 판정할 수 없습니다.",
                evidence(null, reason));
    }

    /**
     * 객관식은 {@code choiceKey} 문자열로 대조한다.
     *
     * <p>{@code RuleGrader.gradeChoice} 를 쓰지 않는다. 그 메서드는 DB 의 보기 ID 두 개를 받는데
     * 스냅샷에는 ID 가 없고 {@code answerRaw} 에 {@code choiceKey} 가 들어 있다
     * (Validator 가 실제 choiceKey 를 참조하도록 강제한다). {@code RuleGrader.grade} 의 CHOICE 분기는
     * "보기 ID 로 채점한다"며 실패를 돌려주므로 그쪽도 쓸 수 없다.
     * {@code domain.grading} 을 고치지 않기로 했으므로 대조는 여기서 한다.
     */
    private VerificationFinding multipleChoice(QuestionSnapshotV1 snapshot, SolverAnswer solverAnswer) {
        SnapshotAnswerUnit unit = snapshot.answerUnits().getFirst();
        String solved = trim(solverAnswer.answerFor(unit.unitKey()));
        String correct = trim(unit.answerRaw());

        if (correct == null) {
            return Findings.error(VerificationCheckType.CORRECTNESS,
                    "정답 보기가 스냅샷에 없어 정확성을 판정할 수 없습니다.", null);
        }
        if (correct.equals(solved)) {
            return Findings.pass(VerificationCheckType.CORRECTNESS,
                    "검증기가 독립적으로 푼 답이 정답 보기와 일치합니다.");
        }
        return Findings.fail(
                VerificationCheckType.CORRECTNESS,
                VerificationIssueCode.ANSWER_INCORRECT,
                "검증기가 고른 보기가 정답과 다릅니다.",
                evidence(solved, solverAnswer.reason()));
    }

    /** 단답형과 STEP_FILL. STEP_FILL 은 칸별로 보고 하나라도 틀리면 FAIL 이다. */
    private VerificationFinding compareByUnit(QuestionSnapshotV1 snapshot, SolverAnswer solverAnswer) {
        List<String> wrongKeys = new ArrayList<>();
        // 근거에는 처음 틀린 칸의 답을 담는다. 맞은 칸의 답을 담으면 근거가 판정과 어긋나 보인다.
        String firstWrongAnswer = null;

        for (SnapshotAnswerUnit unit : snapshot.answerUnits()) {
            if (unit == null) {
                continue;
            }
            CompareMethod compareMethod = unit.compareMethod();
            if (compareMethod == null) {
                return Findings.error(VerificationCheckType.CORRECTNESS,
                        "비교 방법이 없어 정확성을 판정할 수 없습니다: " + unit.unitKey(), null);
            }

            String solved = solverAnswer.answerFor(unit.unitKey());
            String correctNormalized = answerNormalizer.normalize(unit.answerRaw(), unit.displayUnit());
            String solvedNormalized = answerNormalizer.normalize(solved, unit.displayUnit());
            RuleGrader.Verdict verdict =
                    ruleGrader.grade(compareMethod, solvedNormalized, correctNormalized);
            if (!verdict.correct()) {
                wrongKeys.add(unit.unitKey());
                if (firstWrongAnswer == null) {
                    firstWrongAnswer = solved;
                }
            }
        }

        if (wrongKeys.isEmpty()) {
            return Findings.pass(VerificationCheckType.CORRECTNESS,
                    "검증기가 독립적으로 푼 답이 모든 칸에서 정답과 일치합니다.");
        }
        return Findings.fail(
                VerificationCheckType.CORRECTNESS,
                VerificationIssueCode.ANSWER_INCORRECT,
                "검증기가 푼 답이 정답과 다릅니다 — 불일치한 칸: " + String.join(", ", wrongKeys),
                evidence(firstWrongAnswer, solverAnswer.reason()));
    }

    /**
     * {@code evidence} 에는 최종 답과 한 줄 근거만 담는다.
     *
     * <p>전체 풀이 과정을 넣지 않는다. 이 값은 저장되고 조회되며, 문항 본문과 풀이가 로그·DB 로
     * 흘러가면 정답 유출 정책이 무너진다. 로깅 정책과 같은 기준을 쓴다.
     */
    private static String evidence(String finalAnswer, String reason) {
        String oneLine = reason == null ? "" : reason.lines().findFirst().orElse("").strip();
        if (finalAnswer == null) {
            return oneLine.isEmpty() ? null : "근거: " + oneLine;
        }
        return oneLine.isEmpty()
                ? "검증기 답: " + finalAnswer
                : "검증기 답: " + finalAnswer + " · 근거: " + oneLine;
    }

    private static String trim(String value) {
        return value == null ? null : value.strip();
    }
}
