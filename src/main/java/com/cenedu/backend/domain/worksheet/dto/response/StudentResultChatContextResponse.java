package com.cenedu.backend.domain.worksheet.dto.response;

/**
 * 복습 화면에서 개념 챗봇을 열 때 넘길 맥락.
 *
 * <p>{@code conceptId}를 만들지 않는다. 개념 식별은 {@code /api/chat}이 소단원과 학생 질문에서
 * 앵커를 직접 찾는 일이라, 서버가 개념을 미리 정해 주면 앵커 탐색을 우회하게 되어 학생이 물은
 * 것과 다른 개념에 고정된다.
 */
public record StudentResultChatContextResponse(Long subUnitId, String conceptLabel) {
}
