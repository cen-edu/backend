package com.cenedu.backend.domain.analysis.reissue;

import com.cenedu.backend.domain.analysis.entity.AnalysisAttempt;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 이전 문제지에서 체류 난이도를 읽는 규칙.
 *
 * <p>따로 저장하지 않고 그 한 회분의 문항 난이도에서 읽는다.
 */
class DwellDifficultyTest {

    // ---- 재출제로 만든 문제지: 난이도가 하나뿐이다 ----

    /** 성적을 보지 않는다. 다 맞혀도 체류 난이도는 그 문제지의 난이도다. */
    @Test
    void aSingleBandSheetIsReadAsIsWhenEverythingIsCorrect() {
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "high", true),
                row("P-2", "high", true),
                row("P-3", "high", true))));
    }

    /**
     * 다 틀려도 마찬가지다. 내려가는 것은 상태(NEEDS_SUPPORT)가 시킨다.
     * 여기서까지 내리면 같은 강등이 두 번 걸린다.
     */
    @Test
    void aSingleBandSheetIsReadAsIsWhenEverythingIsWrong() {
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "high", false),
                row("P-2", "high", false),
                row("P-3", "high", false))));
    }

    // ---- 교사가 낸 첫 진단평가: 난이도가 섞여 있다 ----

    /** 하 하나 · 중 하나 · 상 하나. 아래부터 통과하는지 본다. */
    @Test
    void aMixedSheetStopsAtTheFirstBlockedRung() {
        // 전부 맞힘 → 막힌 칸이 없다
        assertEquals(QuestionDifficulty.HIGH, mixed(true, true, true));
        // 하·중 맞고 상 틀림 → 상에서 막힘
        assertEquals(QuestionDifficulty.HIGH, mixed(true, true, false));
        // 하만 맞음 → 중에서 막힘
        assertEquals(QuestionDifficulty.MEDIUM, mixed(true, false, false));
        // 하나도 못 맞힘 → 하에서 막힘
        assertEquals(QuestionDifficulty.LOW, mixed(false, false, false));
    }

    /**
     * 상을 맞고 하를 틀린 경우. 맞힌 것 중 최고를 쓰면 상 근처에 놓이지만,
     * 아래부터 보면 하에서 막혔다. 찍었을 수도 있는 한 문항보다 낮은 쪽 실패가 무겁다.
     */
    @Test
    void aHighHitWithALowMissFallsToTheBottom() {
        assertEquals(QuestionDifficulty.LOW, mixed(false, false, true));
        assertEquals(QuestionDifficulty.LOW, mixed(false, true, true));
    }

    /** 그 난이도 문항이 아예 없으면 건너뛴다. 없는 것을 막힌 것으로 보지 않는다. */
    @Test
    void anAbsentRungIsSkipped() {
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "low", true),
                row("P-2", "high", true))));
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "mid", true),
                row("P-2", "high", true))));
    }

    /** 틀린 문항이 절반 이상이면 막힌 것이다. 지원 필요를 보는 식과 같다. */
    @Test
    void halfWrongIsEnoughToBlockARung() {
        assertEquals(QuestionDifficulty.LOW, ReissueService.dwellDifficulty(List.of(
                row("P-1", "low", true),
                row("P-2", "low", false),
                row("P-3", "high", true))));
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "low", true),
                row("P-2", "low", true),
                row("P-3", "low", false),
                row("P-4", "high", true))));
    }

    // ---- 난이도를 읽을 수 없는 행 ----

    /** UNKNOWN은 빼고 나머지로 판단한다. 모르는 것을 중간값으로 채우지 않는다. */
    @Test
    void unknownBandsAreIgnored() {
        assertEquals(QuestionDifficulty.HIGH, ReissueService.dwellDifficulty(List.of(
                row("P-1", "UNKNOWN", false),
                row("P-2", "high", true),
                row("P-3", "high", true))));
    }

    /** 전부 UNKNOWN이면 근거가 없다. 중에서 시작한다. */
    @Test
    void everythingUnknownStartsFromTheMiddle() {
        assertEquals(QuestionDifficulty.MEDIUM, ReissueService.dwellDifficulty(List.of(
                row("P-1", "UNKNOWN", true),
                row("P-2", "UNKNOWN", false))));
    }

    /** 같은 문항을 여러 번 풀어도 한 문항으로 센다. 한 번이라도 틀리면 틀린 문항이다. */
    @Test
    void repeatedAttemptsOnOneProblemCountOnce() {
        assertEquals(QuestionDifficulty.LOW, ReissueService.dwellDifficulty(List.of(
                row("P-1", "low", false),
                row("P-1", "low", true),
                row("P-2", "high", true))));
    }

    // ---- 사다리 ----

    @Test
    void theLadderMovesWithTheStatus() {
        assertEquals(QuestionDifficulty.LOW, ReissueService.nextDifficulty(
                LearningStatus.NEEDS_SUPPORT, QuestionDifficulty.MEDIUM));
        assertEquals(QuestionDifficulty.MEDIUM, ReissueService.nextDifficulty(
                LearningStatus.WATCH, QuestionDifficulty.MEDIUM));
        assertEquals(QuestionDifficulty.HIGH, ReissueService.nextDifficulty(
                LearningStatus.CLEAR, QuestionDifficulty.MEDIUM));
    }

    /** 양 끝에서는 더 움직이지 않는다. */
    @Test
    void theLadderStopsAtBothEnds() {
        assertEquals(QuestionDifficulty.LOW, ReissueService.nextDifficulty(
                LearningStatus.NEEDS_SUPPORT, QuestionDifficulty.LOW));
        assertEquals(QuestionDifficulty.HIGH, ReissueService.nextDifficulty(
                LearningStatus.CLEAR, QuestionDifficulty.HIGH));
    }

    private static QuestionDifficulty mixed(boolean low, boolean mid, boolean high) {
        return ReissueService.dwellDifficulty(List.of(
                row("P-LOW", "low", low),
                row("P-MID", "mid", mid),
                row("P-HIGH", "high", high)));
    }

    private static AnalysisAttempt row(
            String problemId, String difficultyBand, boolean correct) {
        return AnalysisAttempt.builder()
                .eventId("E-" + problemId + "-" + correct)
                .assessmentId("A-1").studentId("L-1")
                .problemNumber(1).problemId(problemId).problemTitle(problemId)
                .conceptId("GCD").stepId("GCD_COMPUTE")
                .correct(correct).hintUsed(false).submissionFailed(false)
                .difficultyBand(difficultyBand)
                .occurredAt(Instant.parse("2026-08-07T00:00:00Z"))
                .build();
    }
}
