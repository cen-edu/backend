package com.cenedu.backend.domain.analysis.service;

import com.cenedu.backend.domain.analysis.entity.enums.MasteryStatus;

/**
 * 상·중·하 3단 난이도 사다리.
 *
 * <p>문제 은행의 {@code problem_question.difficulty}(1=하, 2=중, 3=상)와 같은 축이다. 상에서
 * 다시 승급하거나 하에서 다시 강등되는 일이 없도록 양 끝에서 값을 붙잡는다.
 */
public final class DifficultyLadder {

    /** 하 난이도. 기초 부족 상태의 시작점이다. */
    public static final short LOW = 1;

    /** 중 난이도. */
    public static final short MID = 2;

    /** 상 난이도. Phase 3(응용) 발동 조건의 전제다. */
    public static final short HIGH = 3;

    private DifficultyLadder() {
    }

    /** 판정 결과를 현재 난이도에 적용한다. 상·하한을 넘으면 끝값에서 멈춘다. */
    public static short apply(short currentDifficulty, MasteryStatus status) {
        return clamp(currentDifficulty + status.difficultyDelta());
    }

    /** 1~3 범위를 벗어난 값을 끝값으로 붙잡는다. */
    public static short clamp(int difficulty) {
        if (difficulty < LOW) {
            return LOW;
        }
        if (difficulty > HIGH) {
            return HIGH;
        }
        return (short) difficulty;
    }

    /** 1~3 이 아닌 난이도를 걸러 낸다. 문제 은행 값이 이 범위를 벗어나면 판정이 무의미하다. */
    public static boolean isValid(int difficulty) {
        return difficulty >= LOW && difficulty <= HIGH;
    }

    /**
     * 저장값을 API 코드로 바꾼다.
     *
     * <p>AGENTS.md 3절 3번에 따라 프론트 {@code labels.js} 와 같은 소문자 코드를 쓴다. 한글
     * 표기는 서버가 붙이지 않는다.
     */
    public static String code(short difficulty) {
        return switch (difficulty) {
            case LOW -> "low";
            case MID -> "mid";
            case HIGH -> "high";
            default -> throw new IllegalArgumentException("지원하지 않는 난이도입니다: " + difficulty);
        };
    }
}
