package com.cenedu.backend.domain.analysis.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.cenedu.backend.domain.analysis.dto.AttemptResult;
import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.AttemptPurpose;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;

/**
 * 응답 기록을 상태로 바꾸는 순수 함수.
 *
 * <p>규칙은 네 줄이다.
 * <ol>
 *   <li>오류가 없으면 CLEAR</li>
 *   <li>오류가 보이면 WATCH</li>
 *   <li>서로 다른 문항 2개 이상에서, 그 단계 문항의 절반 이상을 틀리면 NEEDS_SUPPORT</li>
 *   <li>그 뒤 힌트 없이 3회 연속 정답이면 IMPROVED</li>
 * </ol>
 *
 * <p>지원 기준을 개수가 아니라 비율로 잡는다. 판정 범위가 문제지 한 회분인데 문제지 길이가
 * 5~20문항으로 달라지기 때문이다. "오류 3회"로 두면 3문항짜리 재출제 문제지에서는 전멸을 뜻하고
 * 10문항 평가에서는 30%를 뜻해, 같은 상태가 회차마다 다른 것을 가리킨다.
 *
 * <p>"서로 다른 문항 2개 이상"은 비율과 별개로 유지한다. 한 문항만 반복해 틀린 것은 그 문항이
 * 어려운 것일 수 있어 단계의 취약 근거로 삼지 않는다.
 *
 * <p>이 규칙은 {@link AttemptPurpose#DIAGNOSTIC} 응답에만 적용한다. 응용 응답을 섞으면 응용 오답
 * 한 번에 IMPROVED 가 WATCH 로 돌아가, 개념을 습득한 학생이 다시 지원 대상으로 내려간다.
 */
public final class WeaknessAnalyzer {

    private static final int DISTINCT_PROBLEMS_FOR_SUPPORT = 2;
    private static final int CORRECT_FOR_IMPROVEMENT = 3;

    private WeaknessAnalyzer() {
    }

    public static LearningState analyze(List<AttemptResult> input) {
        List<AttemptResult> all = prepare(input);
        AttemptResult first = all.get(0);
        requireSameKey(all, first);

        List<AttemptResult> attempts = new ArrayList<>();
        int appliedCount = 0;
        int appliedCorrect = 0;
        for (AttemptResult attempt : all) {
            if (attempt.purpose() == AttemptPurpose.APPLIED) {
                appliedCount++;
                if (attempt.correct()) {
                    appliedCorrect++;
                }
                continue;
            }
            attempts.add(attempt);
        }
        if (attempts.isEmpty()) {
            throw new IllegalArgumentException(
                    "진단 응답이 최소 1개 필요합니다. 응용 응답만으로는 상태를 정할 수 없습니다.");
        }

        // 비율의 분모. 이 단계에서 실제로 낸 문항 수다. 같은 문항을 여러 번 풀어도 한 문항으로 센다.
        Set<String> allProblems = new LinkedHashSet<>();
        for (AttemptResult attempt : attempts) {
            allProblems.add(attempt.problemId());
        }

        LearningStatus status = LearningStatus.CLEAR;
        int errorCount = 0;
        Set<String> errorProblems = new LinkedHashSet<>();
        int consecutiveIndependentCorrect = 0;

        for (AttemptResult attempt : attempts) {
            if (!attempt.correct()) {
                if (status == LearningStatus.IMPROVED) {
                    // 다시 오류가 보이면 새 관찰 구간을 시작한다.
                    status = LearningStatus.WATCH;
                    errorCount = 0;
                    errorProblems.clear();
                } else if (status == LearningStatus.CLEAR) {
                    status = LearningStatus.WATCH;
                }
                errorCount++;
                errorProblems.add(attempt.problemId());
                consecutiveIndependentCorrect = 0;

                if (status == LearningStatus.WATCH
                        && needsSupport(errorProblems.size(), allProblems.size())) {
                    status = LearningStatus.NEEDS_SUPPORT;
                }
                continue;
            }

            if (status == LearningStatus.NEEDS_SUPPORT) {
                if (attempt.hintUsed()) {
                    // 힌트 후 정답은 학습 성과지만 독립 해결 연속 기록에는 넣지 않는다.
                    consecutiveIndependentCorrect = 0;
                } else {
                    consecutiveIndependentCorrect++;
                    if (consecutiveIndependentCorrect >= CORRECT_FOR_IMPROVEMENT) {
                        status = LearningStatus.IMPROVED;
                    }
                }
            }
        }

        return new LearningState(
                first.learnerId(),
                first.conceptId(),
                first.stepId(),
                status,
                errorCount,
                errorProblems.size(),
                consecutiveIndependentCorrect,
                appliedCount,
                appliedCorrect
        );
    }

    /**
     * 지원이 필요한지 본다.
     *
     * <p>서로 다른 문항 2개 이상이면서, 그 단계에 낸 문항의 절반 이상을 틀렸을 때다. 문제지 길이에
     * 상관없이 같은 뜻을 갖는다.
     *
     * <table>
     *   <caption>낸 문항 수별 지원 필요 기준</caption>
     *   <tr><th>낸 문항</th><th>지원 필요 기준</th></tr>
     *   <tr><td>2</td><td>2개</td></tr>
     *   <tr><td>3</td><td>2개</td></tr>
     *   <tr><td>5</td><td>3개</td></tr>
     *   <tr><td>10</td><td>5개</td></tr>
     * </table>
     */
    private static boolean needsSupport(int wrongProblems, int totalProblems) {
        return wrongProblems >= DISTINCT_PROBLEMS_FOR_SUPPORT
                && wrongProblems * 2 >= totalProblems;
    }

    private static List<AttemptResult> prepare(List<AttemptResult> input) {
        if (input == null || input.isEmpty()) {
            throw new IllegalArgumentException("응답 기록이 최소 1개 필요합니다.");
        }

        Map<String, AttemptResult> byEventId = new LinkedHashMap<>();
        for (AttemptResult attempt : input) {
            if (attempt == null) {
                throw new IllegalArgumentException("응답 기록에 null이 들어갈 수 없습니다.");
            }
            AttemptResult existing = byEventId.putIfAbsent(attempt.eventId(), attempt);
            if (existing != null && !existing.equals(attempt)) {
                throw new IllegalArgumentException(
                        "같은 eventId에 서로 다른 응답이 있습니다: " + attempt.eventId());
            }
        }

        List<AttemptResult> result = new ArrayList<>(byEventId.values());
        result.sort(Comparator.comparing(AttemptResult::occurredAt)
                .thenComparing(AttemptResult::eventId));
        return result;
    }

    private static void requireSameKey(List<AttemptResult> attempts, AttemptResult first) {
        for (AttemptResult attempt : attempts) {
            boolean same = first.learnerId().equals(attempt.learnerId())
                    && first.conceptId().equals(attempt.conceptId())
                    && first.stepId().equals(attempt.stepId());
            if (!same) {
                throw new IllegalArgumentException(
                        "analyze()에는 같은 학생·개념·단계의 응답만 전달해야 합니다.");
            }
        }
    }
}
