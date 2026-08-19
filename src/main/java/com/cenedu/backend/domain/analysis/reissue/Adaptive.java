package com.cenedu.backend.domain.analysis.reissue;

import java.math.BigDecimal;

import com.cenedu.backend.domain.analysis.entity.enums.CutoffRule;
import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;
import com.cenedu.backend.domain.analysis.service.DifficultyLadder;

/**
 * 한 소단원의 현재 난이도와 그렇게 정해진 경위.
 *
 * <p>응답에 그대로 나가는 값과 설명 문장을 쓰는 데만 필요한 값이 섞여 있다. 문장이 근거로 쓰는
 * 숫자를 응답에서 다시 계산하지 않게 하려고 한 곳에 모은다 — 두 번 계산하면 문장과 필드가
 * 어긋날 수 있다.
 *
 * @param source                {@code placement} / {@code judgement} / {@code default}
 * @param difficultyBefore      조절 직전 난이도. 진단으로 정해졌으면 결과와 같다
 * @param placementSoleDifficulty 단일 난이도 진단에서 출제된 난이도. 혼합·판정이면 {@code null}
 */
record Adaptive(
        short difficulty,
        String source,
        short difficultyBefore,
        BigDecimal placementRate,
        Boolean placementMixed,
        Short placementSoleDifficulty,
        MasteryStatus lastStatus,
        CutoffRule cutoffRule,
        int similarTotalCount,
        int similarCorrectCount,
        BigDecimal accuracyRate,
        boolean advancedTriggered
) {

    /** 근거가 하나도 없을 때. 위아래 어느 쪽으로도 조절할 수 있는 가운데에 세운다. */
    static Adaptive unknown(short fallbackDifficulty) {
        return new Adaptive(fallbackDifficulty, "default", fallbackDifficulty,
                null, null, null, null, null, 0, 0, null, false);
    }

    /** 진단 평가로 시작 난이도를 정한 경우. */
    static Adaptive fromPlacement(short difficulty, BigDecimal rate, boolean mixed,
                                  Short soleDifficulty) {
        return new Adaptive(difficulty, "placement", difficulty, rate, mixed, soleDifficulty,
                null, null, 0, 0, null, false);
    }

    /** 직전 회차의 유사 문항 결과로 난이도를 조절한 경우. */
    static Adaptive fromJudgement(short difficultyBefore, int totalCount, int correctCount,
                                  MasteryStatus status, CutoffRule cutoffRule,
                                  BigDecimal accuracyRate, short difficultyAfter,
                                  boolean advancedTriggered) {
        return new Adaptive(difficultyAfter, "judgement", difficultyBefore, null, null, null,
                status, cutoffRule, totalCount, correctCount, accuracyRate, advancedTriggered);
    }

    /** 응답에 실을 부분만 추린다. */
    ReissueProposalResponse.AdaptiveState toResponse(int customSessionCount) {
        return new ReissueProposalResponse.AdaptiveState(
                DifficultyLadder.code(difficulty), source, placementRate, placementMixed,
                customSessionCount, lastStatus);
    }
}
