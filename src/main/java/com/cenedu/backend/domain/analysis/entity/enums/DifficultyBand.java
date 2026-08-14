package com.cenedu.backend.domain.analysis.entity.enums;

/** 종합평가 화면에서 사용하는 상·중·하 난이도 구간. */
public enum DifficultyBand {
    HIGH(3),
    MID(2),
    LOW(1);

    private final int sourceDifficulty;

    DifficultyBand(int sourceDifficulty) {
        this.sourceDifficulty = sourceDifficulty;
    }

    /** 문제 은행에 저장된 1·2·3 난이도를 하·중·상 구간으로 변환한다. */
    public static DifficultyBand from(int sourceDifficulty) {
        for (DifficultyBand band : values()) {
            if (band.sourceDifficulty == sourceDifficulty) {
                return band;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 난이도입니다: " + sourceDifficulty);
    }
}
