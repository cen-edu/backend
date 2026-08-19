package com.cenedu.backend.domain.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.cenedu.backend.domain.analysis.service.PlacementScorer.PlacementResult;
import com.cenedu.backend.domain.analysis.service.PlacementScorer.PlacementTally;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 설계서 2절의 가중 배점과 컷오프를 고정한다. */
class PlacementScorerTest {

    @Test
    @DisplayName("난이도를 배점(하 2·중 3·상 4)으로 환산해 가중 성취도를 낸다")
    void weightsByDifficultyScore() {
        // 하 2문항 중 2개, 중 2문항 중 1개, 상 2문항 중 0개
        // 획득 (2×2)+(3×1)+(4×0) = 7, 최대 (2×2)+(3×2)+(4×2) = 18
        PlacementResult result = PlacementScorer.score(new PlacementTally(2, 2, 2, 1, 2, 0));

        assertThat(result.earnedScore()).isEqualTo(7);
        assertThat(result.maxScore()).isEqualTo(18);
        assertThat(result.rate()).isEqualByComparingTo("38.89");
        assertThat(result.difficulty()).isEqualTo(DifficultyLadder.LOW);
        assertThat(result.mixed()).isTrue();
    }

    @Test
    @DisplayName("문항 수가 같아도 고배점 문항을 맞힌 쪽이 위로 간다")
    void rewardsHighDifficultyCorrectness() {
        // 하 3 + 상 3 출제, 최대 (2×3)+(4×3) = 18
        PlacementResult lowSolver = PlacementScorer.score(new PlacementTally(3, 3, 0, 0, 3, 0));
        PlacementResult highSolver = PlacementScorer.score(new PlacementTally(3, 0, 0, 0, 3, 3));

        assertThat(lowSolver.rate()).isEqualByComparingTo("33.33");
        assertThat(highSolver.rate()).isEqualByComparingTo("66.67");
        assertThat(lowSolver.difficulty()).isEqualTo(DifficultyLadder.LOW);
        assertThat(highSolver.difficulty()).isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("혼합 진단지는 80% / 40% 절대 컷오프를 쓴다")
    void appliesAbsoluteCutoffWhenMixed() {
        // 상 7문항 정답 + 하 3문항 오답 → 28/34 = 82.35%
        assertThat(PlacementScorer.score(new PlacementTally(3, 0, 0, 0, 7, 7)).difficulty())
                .isEqualTo(DifficultyLadder.HIGH);
        // 하 5문항 중 2개 + 상 5문항 중 2개 → (4+8)/(10+20) = 40%
        assertThat(PlacementScorer.score(new PlacementTally(5, 2, 0, 0, 5, 2)).difficulty())
                .isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("진단 상위 컷오프는 메인 평가의 승급 컷오프와 같은 값이다")
    void sharesClearCutoffWithMainLoop() {
        assertThat(PlacementScorer.HIGH_CUTOFF)
                .isEqualByComparingTo(MasteryStatusJudge.CLEAR_CUTOFF);
        assertThat(PlacementScorer.MID_CUTOFF)
                .isEqualByComparingTo(MasteryStatusJudge.SUPPORT_CUTOFF);
    }

    @Test
    @DisplayName("중 난이도만 출제된 진단지는 설계서 2.3 예시대로 배정한다")
    void followsSingleBandExample() {
        // 80% 이상 → 상
        PlacementResult cleared = PlacementScorer.score(new PlacementTally(0, 0, 5, 4, 0, 0));
        assertThat(cleared.mixed()).isFalse();
        assertThat(cleared.difficulty()).isEqualTo(DifficultyLadder.HIGH);

        // 40~79% → 중
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 4, 3, 0, 0)).difficulty())
                .isEqualTo(DifficultyLadder.MID);

        // 40% 미만 → 하
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 5, 1, 0, 0)).difficulty())
                .isEqualTo(DifficultyLadder.LOW);
    }

    @Test
    @DisplayName("하 난이도만 다 맞혀도 상까지 뛰지 않는다 — 풀지 않은 난이도를 실력으로 보지 않는다")
    void neverSkipsALadderStep() {
        PlacementResult result = PlacementScorer.score(new PlacementTally(5, 5, 0, 0, 0, 0));

        assertThat(result.rate()).isEqualByComparingTo("100.00");
        assertThat(result.difficulty()).isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("상 난이도만 출제된 진단지에서 부진하면 한 칸 아래인 중에서 시작한다")
    void demotesOneStepFromHighBand() {
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 0, 0, 5, 1)).difficulty())
                .isEqualTo(DifficultyLadder.MID);
    }

    @Test
    @DisplayName("채점된 문항이 없으면 난이도를 찍지 않는다")
    void returnsNullWithoutGradedQuestions() {
        assertThat(PlacementScorer.score(new PlacementTally(0, 0, 0, 0, 0, 0))).isNull();
    }
}
