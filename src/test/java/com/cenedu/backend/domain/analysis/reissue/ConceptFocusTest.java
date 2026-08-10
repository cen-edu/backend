package com.cenedu.backend.domain.analysis.reissue;

import java.util.List;

import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 개념 하나가 화면의 세 칸에 몇 문항으로 번역되는지.
 *
 * <p>모드를 하나 고르던 규칙을 칸별 문항 수로 편 것이라, 규칙 자체는 같아야 한다.
 */
class ConceptFocusTest {

    private static final int SET = 3;

    /** 복습은 기록되지 않은 문항 수 그대로다. 새로 고르는 것이 아니라 그 문항을 다시 낸다. */
    @Test
    void retraceCountsOnlyLostSubmissions() {
        assertEquals(0, focus(LearningStatus.WATCH, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.RETRACE, SET));
        assertEquals(2, focus(LearningStatus.WATCH, QuestionDifficulty.MEDIUM,
                List.of("P-1", "P-2")).proposedCount(ReissueStage.RETRACE, SET));
    }

    /**
     * 답을 쓴 학생에게는 복습이 0이다. 상태가 아무리 나빠도 마찬가지다. 같은 문항을 다시 내면
     * 실력이 아니라 기억을 잰다.
     */
    @Test
    void retraceStaysZeroForAnsweredProblems() {
        assertEquals(0, focus(LearningStatus.NEEDS_SUPPORT, QuestionDifficulty.LOW, List.of())
                .proposedCount(ReissueStage.RETRACE, SET));
    }

    /** 유사는 오류가 관찰됐을 때만. 오류가 없으면 골라도 볼 것이 없다. */
    @Test
    void basicNeedsAnObservedError() {
        assertEquals(SET, focus(LearningStatus.WATCH, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.BASIC, SET));
        assertEquals(SET, focus(LearningStatus.NEEDS_SUPPORT, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.BASIC, SET));
        assertEquals(0, focus(LearningStatus.CLEAR, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.BASIC, SET));
        assertEquals(0, focus(LearningStatus.IMPROVED, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.BASIC, SET));
    }

    /**
     * 응용은 사다리를 끝까지 올라갔을 때만. 하 문제지를 다 맞힌 것과 상 문제지를 다 맞힌 것은
     * 다르다. 이 검사가 없으면 하 문제지를 다 맞힌 학생이 곧바로 응용을 받는다.
     */
    @Test
    void independentNeedsTheTopRung() {
        assertEquals(0, focus(LearningStatus.CLEAR, QuestionDifficulty.LOW, List.of())
                .proposedCount(ReissueStage.INDEPENDENT, SET));
        assertEquals(0, focus(LearningStatus.CLEAR, QuestionDifficulty.MEDIUM, List.of())
                .proposedCount(ReissueStage.INDEPENDENT, SET));
        assertEquals(SET, focus(LearningStatus.CLEAR, QuestionDifficulty.HIGH, List.of())
                .proposedCount(ReissueStage.INDEPENDENT, SET));
        assertEquals(SET, focus(LearningStatus.IMPROVED, QuestionDifficulty.HIGH, List.of())
                .proposedCount(ReissueStage.INDEPENDENT, SET));
    }

    /** 상에 있어도 오류가 보이면 응용이 아니다. */
    @Test
    void anErrorAtTheTopRungIsNotAppliedYet() {
        assertEquals(0, focus(LearningStatus.WATCH, QuestionDifficulty.HIGH, List.of())
                .proposedCount(ReissueStage.INDEPENDENT, SET));
        assertEquals(SET, focus(LearningStatus.WATCH, QuestionDifficulty.HIGH, List.of())
                .proposedCount(ReissueStage.BASIC, SET));
    }

    /**
     * 복습과 유사는 한 개념에서 동시에 0보다 클 수 있다. 어떤 문항은 기록되지 않았고 다른
     * 문항에서는 오류가 관찰된 경우다. 모드를 하나 고르던 때와 달라지는 지점이다.
     */
    @Test
    void retraceAndBasicCanBothBePositive() {
        ConceptFocus focus = focus(LearningStatus.WATCH, QuestionDifficulty.MEDIUM,
                List.of("P-LOST"));
        assertEquals(1, focus.proposedCount(ReissueStage.RETRACE, SET));
        assertEquals(SET, focus.proposedCount(ReissueStage.BASIC, SET));
    }

    /** 모든 응답이 기록되지 않았으면 읽을 상태가 없다. 복습만 남는다. */
    @Test
    void withoutAStateOnlyRetraceRemains() {
        ConceptFocus focus = new ConceptFocus("GCD", "최대공약수", "GCD_COMPUTE",
                "최대공약수와 최소공배수",
                null, QuestionDifficulty.MEDIUM, QuestionDifficulty.MEDIUM,
                List.of("P-1"), List.of(), List.of(), null, null);
        assertEquals(1, focus.proposedCount(ReissueStage.RETRACE, SET));
        assertEquals(0, focus.proposedCount(ReissueStage.BASIC, SET));
        assertEquals(0, focus.proposedCount(ReissueStage.INDEPENDENT, SET));
    }

    private static ConceptFocus focus(
            LearningStatus status, QuestionDifficulty dwell, List<String> lost) {
        LearningState state = new LearningState(
                "L-1", "GCD", "GCD_COMPUTE", status, 1, 1, 0, 0, 0);
        return new ConceptFocus("GCD", "최대공약수", "GCD_COMPUTE", "최대공약수와 최소공배수",
                state, dwell, ReissueService.nextDifficulty(status, dwell),
                lost, List.of(2), List.of(), null, null);
    }
}
