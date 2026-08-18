package com.cenedu.backend.domain.problem.authoring.edit;

/** HITL 한 턴이 수집 계속·확인 요청·실행 확인·취소 중 어느 결과인지 나타낸다. */
public enum EditConversationAction {
    CONTINUE_COLLECTION,
    REQUEST_CONFIRMATION,
    CONFIRM_EXECUTION,
    CANCEL
}
