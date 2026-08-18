package com.cenedu.backend.domain.problem.authoring.generation;

/** 생성 슬롯의 공급원을 구분한다. 은행 재사용은 AI 호출이 필요 없다. */
public enum GenerationSlotSource {
    BANK_REUSE,
    AI_GENERATION
}
