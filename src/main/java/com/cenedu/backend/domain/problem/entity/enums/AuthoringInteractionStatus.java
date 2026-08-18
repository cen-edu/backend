package com.cenedu.backend.domain.problem.entity.enums;

/** 자연어 수정 HITL이 수집·최종 확인 중 어느 단계인지 나타낸다. */
public enum AuthoringInteractionStatus {
    IDLE,
    COLLECTING,
    AWAITING_CONFIRMATION
}
