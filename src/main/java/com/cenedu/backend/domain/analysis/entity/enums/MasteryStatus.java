package com.cenedu.backend.domain.analysis.entity.enums;

/**
 * Phase 2(유사 문항) 결과로 내리는 숙달 판정.
 *
 * <p>난이도 조절의 유일한 근거다. Phase 1(동일)·Phase 3(응용) 결과는 여기에 반영하지 않는다.
 *
 * <p>worksheet 도메인의 {@code LearningStatus*} 는 학습지 진행 현황(미시작·제출·채점)을 뜻하는
 * 다른 축이다. 같은 단어를 피하려고 숙달(mastery)로 이름을 잡았다.
 */
public enum MasteryStatus {

    /** 완전학습. 난이도 승급(+1). */
    CLEAR(1),

    /** 근접발달영역. 난이도 유지(0). */
    WATCH(0),

    /** 인지적 과부하. 난이도 강등(-1). */
    NEEDS_SUPPORT(-1);

    private final int difficultyDelta;

    MasteryStatus(int difficultyDelta) {
        this.difficultyDelta = difficultyDelta;
    }

    /** 이 판정이 현재 난이도에 더할 값. 상·하한 처리는 호출부가 한다. */
    public int difficultyDelta() {
        return difficultyDelta;
    }
}
