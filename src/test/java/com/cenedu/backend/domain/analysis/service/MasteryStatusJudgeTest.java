package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.analysis.entity.enums.CutoffRule;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 설계서 4절의 컷오프 표를 그대로 고정한다. 값이 바뀌면 학술 근거도 함께 바뀌어야 한다. */
class MasteryStatusJudgeTest {

    @ParameterizedTest(name = "5문항 중 {0}개 정답이면 {1}")
    @CsvSource({
            "5, CLEAR",
            "4, CLEAR",
            "3, WATCH",
            "2, WATCH",
            "1, NEEDS_SUPPORT",
            "0, NEEDS_SUPPORT"
    })
    @DisplayName("4문항 이상은 정답률 80% / 40% 컷오프를 쓴다")
    void judgesByRatio(int correctCount, MasteryStatus expected) {
        MasteryStatusJudge.Judgement judgement =
                MasteryStatusJudge.judge(DifficultyLadder.MID, 5, correctCount);

        assertThat(judgement.cutoffRule()).isEqualTo(CutoffRule.RATIO);
        assertThat(judgement.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("4문항에서 3개를 맞히면 75%라 승급하지 않는다")
    void keepsBelowClearCutoff() {
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.MID, 4, 3).status())
                .isEqualTo(MasteryStatus.WATCH);
    }

    @ParameterizedTest(name = "{0}문항 중 오답 {1}개면 {2}")
    @CsvSource({
            "3, 0, CLEAR",
            "3, 1, WATCH",
            "3, 2, WATCH",
            "3, 3, NEEDS_SUPPORT",
            "2, 0, CLEAR",
            "2, 1, WATCH",
            "2, 2, NEEDS_SUPPORT",
            "1, 0, CLEAR",
            "1, 1, WATCH"
    })
    @DisplayName("3문항 이하는 오답 개수 컷오프로 자동 전환된다")
    void judgesByWrongCount(int totalCount, int wrongCount, MasteryStatus expected) {
        MasteryStatusJudge.Judgement judgement = MasteryStatusJudge.judge(
                DifficultyLadder.MID, totalCount, totalCount - wrongCount);

        assertThat(judgement.cutoffRule()).isEqualTo(CutoffRule.WRONG_COUNT);
        assertThat(judgement.status()).isEqualTo(expected);
    }

    @Test
    @DisplayName("한 문항만 틀렸을 때는 강등하지 않는다 — 단일 실수와 실력 부족을 구분할 수 없다")
    void neverDemotesOnSingleQuestion() {
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.MID, 1, 0).difficultyAfter())
                .isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("승급·강등은 사다리 양 끝에서 멈춘다")
    void clampsAtLadderEnds() {
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.HIGH, 5, 5).difficultyAfter())
                .isEqualTo(DifficultyLadder.HIGH);
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.LOW, 5, 0).difficultyAfter())
                .isEqualTo(DifficultyLadder.LOW);
    }

    @Test
    @DisplayName("응용은 상 난이도에서 승급 판정이 났을 때만 발동한다")
    void triggersAdvancedOnlyAtHighClear() {
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.HIGH, 5, 5).advancedTriggered())
                .isTrue();
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.MID, 5, 5).advancedTriggered())
                .isFalse();
        assertThat(MasteryStatusJudge.judge(DifficultyLadder.HIGH, 5, 3).advancedTriggered())
                .isFalse();
    }
}
