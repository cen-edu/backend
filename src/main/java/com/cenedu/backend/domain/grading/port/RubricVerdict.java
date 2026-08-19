package com.cenedu.backend.domain.grading.port;

/**
 * 채점 기준 항목 하나에 대한 LLM 판정. <b>3상태다.</b>
 *
 * <p>저장은 2상태다({@code grading_rubric_result.satisfied}). {@link #UNJUDGEABLE}은 행을 만들지
 * 않는다 — "판정하지 않았다"와 "판정했더니 미충족"은 교사가 봐야 할 것이 완전히 다른데, boolean
 * 한 칸에 넣으면 그 구분이 사라진다.
 */
public enum RubricVerdict {

    /** 기준을 충족했다. */
    SATISFIED,

    /** 기준을 충족하지 못했다. <b>읽었는데 안 됐다</b>는 뜻이다. */
    NOT_SATISFIED,

    /** 필기를 읽을 수 없어 충족 여부를 가릴 수 없다. 오답이 아니라 판정 불가다. */
    UNJUDGEABLE
}
