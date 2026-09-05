package com.cenedu.backend.domain.chat.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/** 해설 자료와 페이지 종류는 받지 않는다. 서버가 매 요청마다 조회한다. */
public record ReviewChatRequest(
        @NotBlank(message = "question은 필수입니다.") String question,
        @Valid List<ChatHistoryMessage> history
) {
    public List<ChatHistoryMessage> historyOrEmpty() {
        return history == null ? List.of() : history;
    }
}
