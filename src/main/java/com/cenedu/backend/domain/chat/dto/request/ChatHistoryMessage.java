package com.cenedu.backend.domain.chat.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 클라이언트가 실어 보내는 이전 대화 한 턴.
 *
 * <p><b>서버가 대화를 저장하지 않으므로 이력은 클라이언트가 들고 다닌다</b>(task_25 §0-2 결정 1).
 * 4주 일정 안에서 대화 저장 테이블을 만들지 않기로 한 결정의 결과이며, 그래서 이 필드가
 * 선택이 아니라 <b>기능의 전제</b>다 — 이력이 오지 않으면 파이프라인의 첫 발화 가드가 매 턴
 * 발동해 하향 탐색이 통째로 죽는다(task_24c §0-1).
 *
 * @param role    {@code user} 또는 {@code assistant}. 대소문자는 가리지 않는다
 * @param content 발화 내용
 */
public record ChatHistoryMessage(
        @NotBlank(message = "role은 필수입니다.")
        String role,

        @NotBlank(message = "content는 필수입니다.")
        String content
) {

    /** 프론트가 보내는 값. {@code ChatMessage.Role} 과 이름이 달라 여기서만 매핑한다. */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
}
