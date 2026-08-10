package com.cenedu.backend.domain.analysis.reissue;

/**
 * 맞춤 문제 화면의 세 칸. 개념 하나에 세 값이 동시에 붙는다.
 *
 * <p>{@link ReissueMode} 와 짝이 맞지만 같은 것이 아니다. 모드는 "이 학생에게 지금 무엇을
 * 내야 하는가" 하나를 고르는 값이고, 이 이넘은 화면이 개념마다 세 칸을 함께 보여 주기 위한
 * 축이다. 한 개념에서 복습과 유사가 동시에 0보다 클 수 있다 — 어떤 문항은 기록되지 않았고
 * 다른 문항에서는 오류가 관찰된 경우다.
 */
public enum ReissueStage {

    /**
     * 복습. <b>원본 문항을 그대로 다시 낸다.</b> 시스템 오류로 답이 기록되지 않은 문항만
     * 해당한다. 답을 쓴 학생에게 같은 문항을 다시 내면 실력이 아니라 기억을 잰다.
     */
    RETRACE("retrace", "① 복습"),

    /** 유사. 뱅크에서 같은 소단원·난이도의 다른 문항을 고른다. */
    BASIC("basic", "② 유사"),

    /** 응용. 다른 맥락으로 옮기는지 본다. 생성이 필요해 아직 문항이 나가지 않는다. */
    INDEPENDENT("independent", "③ 응용");

    private final String code;
    private final String label;

    ReissueStage(String code, String label) {
        this.code = code;
        this.label = label;
    }

    /** 화면이 쓰는 코드. 프론트의 {@code customStages} 와 같은 값이다. */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }
}
