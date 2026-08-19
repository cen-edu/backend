package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.analysis.service.PlacementScorer.PlacementResult;
import com.cenedu.backend.domain.analysis.service.PlacementScorer.PlacementTally;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계서 2절의 가중 배점과 컷오프를 고정한다. */
class PlacementScorerTest {

    @Test
    @DisplayName("난이도를 배점으로 환산해 가중 성취도를 낸다")
    void weightsByDifficultyScore() {
        // 하 2문항 중 2개, 중 2문항 중 1개, 상 2문항 중 0개
        // 획득 (1×2)+(2×1)+(3×0) = 4, 최대 (1×2)+(2×2)+(3×2) = 12
        PlacementResult result = PlacementScorer.score(new PlacementTally(2, 2, 2, 1, 2, 0));

        assertThat(result.earnedScore()).isEqualTo(4);
        assertThat(result.maxScore()).isEqualTo(12);
        assertThat(result.rate()).isEqualByComparingTo("33.33");
        assertThat(result.difficulty()).isEqualTo(DifficultyLadder.LOW);
        assertThat(result.mixed()).isTrue();
    }

    @Test
    @DisplayName("문항 수가 같아도 고배점 문항을 맞힌 쪽이 위로 간다")
    void rewardsHighDifficultyCorrectness() {
        PlacementResult lowSolver = PlacementScorer.score(new PlacementTally(3, 3, 0, 0, 3, 0));
        PlacementResult highSolver = PlacementScorer.score(new PlacementTally(3, 0, 0, 0, 3, 3));

        assertThat(lowSolver.rate()).isEqualByComparingTo("25.00");
        assertThat(highSolver.rate()).isEqualByComparingTo("75.00");
        assertThat(lowSolver.difficulty()).isEqualTo(DifficultyLadder.LOW);
        assertThat(highSolver.difficulty()).isEqualTo(DifficultyLadder.HIGH);
    }

    @Test
    @DisplayName("혼합 진단지는 70% / 40% 절대 컷오프를 쓴다")
    void appliesAbsoluteCutoffWhenMixed() {
        // 상 7문항 정답 + 하 3문항 오답 → 21/24 = 87.5%
        assertThat(PlacementScorer.score(new PlacementTally(3, 0, 0, 0, 7, 7)).difficulty())
                .isEqualTo(DifficultyLadder.HIGH);
        // 하 5문항 중 2개 + 상 5문항 중 2개 → (2+6)/(5+15) = 40%
        assertThat(PlacementScorer.score(new PlacementTally(5, 2, 0, 0, 5, 2)).difficulty())
                .isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("한 난이도로만 출제된 진단지는 그 난이도 기준으로 한 칸씩만 움직인다")
    void adjustsRelativelyWhenSingleBand() {
        // 중 5문항 중 4개(80%) → 중의 한 칸 위인 상까지만
        PlacementResult cleared = PlacementScorer.score(new PlacementTally(0, 0, 5, 4, 0, 0));
        assertThat(cleared.mixed()).isFalse();
        assertThat(cleared.difficulty()).isEqualTo(DifficultyLadder.HIGH);

        // 중 4문항 중 3개(75%) → 절대 컷오프였다면 상이지만, 관찰은 중까지다
        PlacementResult watched = PlacementScorer.score(new PlacementTally(0, 0, 4, 3, 0, 0));
        assertThat(watched.rate()).isEqualByComparingTo("75.00");
        assertThat(watched.difficulty()).isEqualTo(DifficultyLadder.MID);

        // 중 5문항 중 1개(20%) → 한 칸 아래인 하
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 5, 1, 0, 0)).difficulty())
                .isEqualTo(DifficultyLadder.LOW);
    }

    @Test
    @DisplayName("하 난이도만으로 다 맞혀도 상까지 뛰지 않는다")
    void neverSkipsALadderStep() {
        assertThat(PlacementScorer.score(new PlacementTally(5, 5, 0, 0, 0, 0)).difficulty())
                .isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("채점된 문항이 없으면 난이도를 찍지 않는다")
    void returnsNullWithoutGradedQuestions() {
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 0, 0, 0, 0))).isNull();
    }
}
