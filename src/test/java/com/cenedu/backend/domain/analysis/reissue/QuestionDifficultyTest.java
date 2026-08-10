package com.cenedu.backend.domain.analysis.reissue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDifficultyTest {

    /** 원본 숫자는 작을수록 어렵다. 이 방향이 뒤집히면 좌절한 학생에게 더 어려운 문항이 간다. */
    @Test
    void smallerSourceLabelIsHarder() {
        assertEquals(QuestionDifficulty.HIGH, QuestionDifficulty.fromSourceLabel("1"));
        assertEquals(QuestionDifficulty.MEDIUM, QuestionDifficulty.fromSourceLabel("2"));
        assertEquals(QuestionDifficulty.LOW, QuestionDifficulty.fromSourceLabel("3"));
    }

    /**
     * 서비스 난이도는 1이 하, 3이 상이다. 110 원본과 정반대다.
     *
     * <p>ERD 6절 난이도 통합표는 110번을 {@code 1/2/3 → 1/2/3}으로 적어 두었는데,
     * 그대로 넣으면 상 문항이 하로 들어간다. 30번은 하→1이라 두 원천이 정반대로
     * 섞인다. 이 검사가 그 방향을 못 박는다.
     */
    @Test
    void serviceDifficultyRunsOppositeToTheSourceLabel() {
        assertEquals(1, QuestionDifficulty.LOW.serviceDifficulty());
        assertEquals(2, QuestionDifficulty.MEDIUM.serviceDifficulty());
        assertEquals(3, QuestionDifficulty.HIGH.serviceDifficulty());

        // 원본 "1"(상)은 서비스 3이 되어야 한다. 1이 되면 뒤집힌 것이다.
        assertEquals(3, QuestionDifficulty.fromSourceLabel("1").serviceDifficulty());
        assertEquals(1, QuestionDifficulty.fromSourceLabel("3").serviceDifficulty());
        // 30번은 뒤집지 않는다.
        assertEquals(1, QuestionDifficulty.fromSourceLabel("하").serviceDifficulty());
        assertEquals(3, QuestionDifficulty.fromSourceLabel("상").serviceDifficulty());
    }

    @Test
    void serviceDifficultyReadsBackToTheSameBand() {
        for (QuestionDifficulty difficulty : QuestionDifficulty.values()) {
            assertEquals(difficulty, QuestionDifficulty.fromServiceDifficulty(
                    difficulty.serviceDifficulty()));
        }
        assertNull(QuestionDifficulty.fromServiceDifficulty(0));
        assertNull(QuestionDifficulty.fromServiceDifficulty(4));
    }

    @Test
    void koreanAndMockLabelsReadTheSameWay() {
        assertEquals(QuestionDifficulty.HIGH, QuestionDifficulty.fromSourceLabel("상"));
        assertEquals(QuestionDifficulty.MEDIUM, QuestionDifficulty.fromSourceLabel("중"));
        assertEquals(QuestionDifficulty.LOW, QuestionDifficulty.fromSourceLabel("하"));
        assertEquals(QuestionDifficulty.HIGH, QuestionDifficulty.fromSourceLabel("GPT_HIGH"));
        assertEquals(QuestionDifficulty.LOW, QuestionDifficulty.fromSourceLabel("gpt_low"));
    }

    /** 모르는 값을 중간값으로 채우지 않는다. */
    @Test
    void unknownLabelIsNotFilledIn() {
        assertNull(QuestionDifficulty.fromSourceLabel("4"));
        assertNull(QuestionDifficulty.fromSourceLabel(""));
        assertNull(QuestionDifficulty.fromSourceLabel(null));
        assertNull(QuestionDifficulty.fromBand("unknown"));
    }

    @Test
    void bandCodeMatchesApiVocabulary() {
        assertEquals("high", QuestionDifficulty.HIGH.band());
        assertEquals("mid", QuestionDifficulty.MEDIUM.band());
        assertEquals("low", QuestionDifficulty.LOW.band());
        assertEquals(QuestionDifficulty.MEDIUM, QuestionDifficulty.fromBand("MEDIUM"));
    }

    // 원본 라벨과 저장된 밴드가 어긋났는지 보는 bandMatchesSource() 는 이관하지 않았다.
    // 적재 경로를 감사하는 용도였는데 backend 에서는 그 경로가 problem 도메인 소관이다.
    // 쓰는 곳이 생기면 그때 함께 옮긴다.

    @Test
    void stepMovesOneLevelAndStopsAtTheEnd() {
        assertEquals(QuestionDifficulty.MEDIUM, QuestionDifficulty.HIGH.easier());
        assertEquals(QuestionDifficulty.LOW, QuestionDifficulty.MEDIUM.easier());
        assertEquals(QuestionDifficulty.LOW, QuestionDifficulty.LOW.easier());
        assertEquals(QuestionDifficulty.MEDIUM, QuestionDifficulty.LOW.harder());
        assertEquals(QuestionDifficulty.HIGH, QuestionDifficulty.MEDIUM.harder());
        assertEquals(QuestionDifficulty.HIGH, QuestionDifficulty.HIGH.harder());
    }
}
