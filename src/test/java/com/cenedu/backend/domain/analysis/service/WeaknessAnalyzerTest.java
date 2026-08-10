package com.cenedu.backend.domain.analysis.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("취약점 판정")
class WeaknessAnalyzerTest {

    private static final Instant T0 = Instant.parse("2026-08-03T09:00:00Z");

    /** 오류가 하나도 없으면 CLEAR다. 기본값 WATCH로 두면 만점 학생에게 확인 문항이 나간다. */
    @Test
    void noErrorIsClear() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", true, false),
                result(2, "P-2", true, false),
                result(3, "P-3", true, false)
        ));

        assertEquals(LearningStatus.CLEAR, state.status());
        assertEquals(0, state.errorCount());
    }

    @Test
    void halfOfTheProblemsWrongAcrossTwoProblemsNeedSupport() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-1", false, false),
                result(3, "P-2", false, false)
        ));

        assertEquals(LearningStatus.NEEDS_SUPPORT, state.status());
        assertEquals(3, state.errorCount());
        assertEquals(2, state.distinctErrorProblemCount());
    }

    /**
     * 같은 기준이 문제지 길이에 상관없이 같은 뜻을 갖는지 본다. 개수 기준이면 10문항짜리에서
     * 3개만 틀려도 지원 필요가 되어, 3문항짜리 재출제 문제지의 전멸과 같은 값이 나온다.
     */
    @Test
    void theSupportBarScalesWithTheWorksheetLength() {
        // 10문항 중 4개 오답. 절반에 못 미쳐 아직 지원 필요가 아니다.
        assertEquals(LearningStatus.WATCH, WeaknessAnalyzer.analyze(
                tenProblems(6)).status());

        // 5개 오답이면 절반을 채운다. 같은 5개라도 3문항짜리 문제지였다면 이미 전멸이다.
        assertEquals(LearningStatus.NEEDS_SUPPORT, WeaknessAnalyzer.analyze(
                tenProblems(5)).status());
    }

    /** 앞의 {@code correctCount}문항을 맞히고 나머지를 틀린 10문항 기록. */
    private static List<AttemptResult> tenProblems(int correctCount) {
        List<AttemptResult> attempts = new ArrayList<>();
        for (int number = 1; number <= 10; number++) {
            attempts.add(result(number, "P-" + number, number <= correctCount, false));
        }
        return attempts;
    }

    /** 재출제 문제지는 3문항이다. 2개를 틀리면 절반을 넘는다. */
    @Test
    void twoOfThreeWrongNeedsSupportOnAReissueSheet() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-2", false, false),
                result(3, "P-3", true, false)
        ));

        assertEquals(LearningStatus.NEEDS_SUPPORT, state.status());
    }

    @Test
    void sameProblemRepeatedDoesNotNeedSupport() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-1", false, false),
                result(3, "P-1", false, false)
        ));

        assertEquals(LearningStatus.WATCH, state.status());
    }

    @Test
    void hintedCorrectDoesNotCountAsIndependentImprovement() {
        // 8문항 중 4개 오답이라 절반을 채워 지원 필요로 들어간다.
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-2", false, false),
                result(3, "P-3", false, false),
                result(4, "P-4", false, false),
                result(5, "P-5", true, false),
                result(6, "P-6", true, true),
                result(7, "P-7", true, false),
                result(8, "P-8", true, false)
        ));

        assertEquals(LearningStatus.NEEDS_SUPPORT, state.status());
        assertEquals(2, state.consecutiveIndependentCorrectCount());
    }

    @Test
    void threeIndependentCorrectAnswersImprove() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-2", false, false),
                result(3, "P-3", false, false),
                result(4, "P-4", true, false),
                result(5, "P-5", true, false),
                result(6, "P-6", true, false)
        ));

        assertEquals(LearningStatus.IMPROVED, state.status());
    }

    @Test
    void appliedErrorDoesNotPullImprovedBackToWatch() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-2", false, false),
                result(3, "P-3", false, false),
                result(4, "P-4", true, false),
                result(5, "P-5", true, false),
                result(6, "P-6", true, false),
                applied(7, "A-1", false)
        ));

        assertEquals(LearningStatus.IMPROVED, state.status());
        assertEquals(3, state.errorCount());
        assertEquals(1, state.appliedAttemptCount());
        assertEquals(0, state.appliedCorrectCount());
    }

    @Test
    void appliedCorrectDoesNotCountAsImprovement() {
        LearningState state = WeaknessAnalyzer.analyze(List.of(
                result(1, "P-1", false, false),
                result(2, "P-2", false, false),
                result(3, "P-3", false, false),
                applied(4, "A-1", true),
                applied(5, "A-2", true),
                applied(6, "A-3", true)
        ));

        assertEquals(LearningStatus.NEEDS_SUPPORT, state.status());
        assertEquals(0, state.consecutiveIndependentCorrectCount());
        assertEquals(3, state.appliedCorrectCount());
    }

    @Test
    void appliedOnlyIsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                WeaknessAnalyzer.analyze(List.of(applied(1, "A-1", true))));
    }

    @Test
    void mixedKeysAreRejected() {
        AttemptResult otherStep = new AttemptResult(
                "E-2", "L-민수", "P-2", "GCD", "GCD_PROPERTY",
                false, false, T0.plusSeconds(1));

        assertThrows(IllegalArgumentException.class, () ->
                WeaknessAnalyzer.analyze(List.of(
                        result(1, "P-1", false, false),
                        otherStep
                )));
    }

    private static AttemptResult applied(int no, String problemId, boolean correct) {
        return new AttemptResult(
                "E-" + no,
                "L-민수",
                problemId,
                "GCD",
                "GCD_COMPUTE",
                correct,
                false,
                T0.plusSeconds(no),
                AttemptPurpose.APPLIED
        );
    }

    private static AttemptResult result(
            int no,
            String problemId,
            boolean correct,
            boolean hintUsed
    ) {
        return new AttemptResult(
                "E-" + no,
                "L-민수",
                problemId,
                "GCD",
                "GCD_COMPUTE",
                correct,
                hintUsed,
                T0.plusSeconds(no)
        );
    }
}
