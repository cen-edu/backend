package com.cenedu.backend.domain.grading.port;

/**
 * 서술형 채점 시도 하나의 결말.
 *
 * <p>판정 내용과 별개로 <b>시도 자체가 성립했는지</b>를 나눈다. 교사에게 "다시 돌려보세요"와
 * "직접 채점하세요"를 가르는 근거가 여기서 나온다.
 */
public enum EssayGradingStatus {

    /** 요청한 기준 항목 전부에 판정이 붙었다. 판정 내용이 옳은지와는 별개다. */
    JUDGED,

    /** 턴 상한에 닿을 때까지 판정이 다 붙지 않았다. 억지로 판정을 만들지 않는다. */
    TURN_LIMIT_REACHED,

    /** 모델이 약속한 JSON 을 내지 않았다. */
    MALFORMED_OUTPUT
}
