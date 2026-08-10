package com.cenedu.backend.domain.analysis.reissue;

import com.cenedu.backend.domain.analysis.dto.LearningState;
import com.cenedu.backend.domain.analysis.entity.LearningStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReissueModeTest {

    /** 같은 문항을 다시 내는 유일한 근거. */
    @Test
    void aSubmissionLostToASystemErrorGoesBackToTheSameQuestion() {
        assertEquals(ReissueMode.SAME,
                mode(LearningStatus.WATCH, "P-1", QuestionDifficulty.MEDIUM));
    }

    /**
     * 오류로 기록되지 않은 응답이 있으면 나머지가 깨끗해 보여도 응용으로 보내지 않는다.
     * 못 본 문항이 있는 채로 "다 안다"고 판단할 수 없다.
     */
    @Test
    void aSystemErrorOutranksEverythingElse() {
        assertEquals(ReissueMode.SAME,
                mode(LearningStatus.CLEAR, "P-1", QuestionDifficulty.HIGH));
    }

    /** 모든 응답이 오류로 사라지면 읽을 상태가 없다. 상태가 없어도 같은 문항으로 간다. */
    @Test
    void worksWithoutAStateWhenEveryResponseWasLost() {
        assertEquals(ReissueMode.SAME,
                ReissueService.mode(null, "P-1", QuestionDifficulty.MEDIUM));
    }

    /**
     * 오류가 없어도 사다리 중간이면 응용으로 보내지 않는다. 한 칸 올려서 유사 문항이다.
     * 이 검사가 없으면 하 문제지를 다 맞힌 학생이 곧바로 응용을 받는다.
     */
    @Test
    void aCleanResultBelowTheTopRungClimbsInsteadOfGoingToApplied() {
        assertEquals(ReissueMode.SIMILAR,
                mode(LearningStatus.CLEAR, null, QuestionDifficulty.LOW));
        assertEquals(ReissueMode.SIMILAR,
                mode(LearningStatus.CLEAR, null, QuestionDifficulty.MEDIUM));
    }

    /** 상까지 올라와서 오류가 없으면 더 줄 것이 없다. */
    @Test
    void aCleanResultAtTheTopRungGoesToApplied() {
        assertEquals(ReissueMode.APPLIED,
                mode(LearningStatus.CLEAR, null, QuestionDifficulty.HIGH));
        assertEquals(ReissueMode.APPLIED,
                mode(LearningStatus.IMPROVED, null, QuestionDifficulty.HIGH));
    }

    /** 상에 있어도 오류가 보이면 응용이 아니라 유사 문항이다. */
    @Test
    void anErrorAtTheTopRungStaysOnSimilar() {
        assertEquals(ReissueMode.SIMILAR,
                mode(LearningStatus.WATCH, null, QuestionDifficulty.HIGH));
        assertEquals(ReissueMode.SIMILAR,
                mode(LearningStatus.NEEDS_SUPPORT, null, QuestionDifficulty.HIGH));
    }

    private static ReissueMode mode(
            LearningStatus status, String failedProblemId, QuestionDifficulty dwell) {
        return ReissueService.mode(state(status), failedProblemId, dwell);
    }

    private static LearningState state(LearningStatus status) {
        return new LearningState("L-1", "GCD", "GCD_COMPUTE", status, 1, 1, 0, 0, 0);
    }
}
